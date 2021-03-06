/*
 * Copyright (c) 2001, Zoltan Farkas All Rights Reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.spf4j.concurrent;

import com.google.common.base.Charsets;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.WillClose;

/**
 * File based Lock implementation, that can be used as IPC method.
 *
 * @author zoly
 */
public final class FileBasedLock implements Lock, java.io.Closeable {

  private static final LoadingCache<String, FileBasedLock> FILE_LOCKS =
          CacheBuilder.newBuilder().weakValues().build(new CacheLoader<String, FileBasedLock>() {
    @Override
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public FileBasedLock load(final String lockFile) throws IOException {
      return new FileBasedLock(new File(lockFile));
    }
  });

  private final RandomAccessFile file;
  private final ReentrantLock jvmLock;
  private FileLock fileLock;

  @SuppressFBWarnings("PREDICTABLE_RANDOM")
  private static int next(final int maxVal) {
    return ThreadLocalRandom.current().nextInt(maxVal);
  }

  private FileBasedLock(final File lockFile) throws IOException {
    file = new RandomAccessFile(lockFile, "rws");
    jvmLock = new ReentrantLock();
    fileLock = null;
  }


  /**
   * Returns a FileBasedLock implementation.
   *
   * FileBasedLock will hold onto a File Descriptor during the entire life of the instance.
   * FileBasedLock is a reentrant lock. (it can be acquired multiple times by the same thread)
   *
   * @param lockFile the file to lock on.
   * @return
   * @throws IOException
   */
  public static FileBasedLock getLock(final File lockFile) throws IOException {
    String filePath = lockFile.getCanonicalPath();
    try {
      return FILE_LOCKS.get(filePath);
    } catch (ExecutionException ex) {
      throw new IOException(ex);
    }
  }

  @Override
  @SuppressFBWarnings("MDM_WAIT_WITHOUT_TIMEOUT")
  public void lock() {
    jvmLock.lock();
    if (jvmLock.getHoldCount() > 1) {
      // reentered, we already have the file lock
      return;
    }
    try {
      fileLock = file.getChannel().lock();
      writeHolderInfo();
    } catch (IOException ex) {
      jvmLock.unlock();
      throw new LockRuntimeException(ex);
    } catch (Throwable ex) {
      jvmLock.unlock();
      throw ex;
    }
  }

  @Override
  @SuppressFBWarnings({"MDM_WAIT_WITHOUT_TIMEOUT", "EXS_EXCEPTION_SOFTENING_HAS_CHECKED", "MDM_THREAD_YIELD"})
  public void lockInterruptibly() throws InterruptedException {
    jvmLock.lockInterruptibly();
    if (jvmLock.getHoldCount() > 1) {
      // reentered, we already have the file lock
      return;
    }
    try {
      final FileChannel channel = file.getChannel();
      boolean interrupted = false;
      //CHECKSTYLE:OFF
      while ((fileLock = channel.tryLock()) == null && !(interrupted = Thread.interrupted())) {
        //CHECKSTYLE:ON
        Thread.sleep(next(1000));
      }
      if (interrupted) {
        throw new InterruptedException();
      }
      writeHolderInfo();
    } catch (InterruptedException | RuntimeException ex) {
      jvmLock.unlock();
      throw ex;
    } catch (IOException ex) {
      jvmLock.unlock();
      throw new LockRuntimeException(ex);
    }
  }

  @Override
  @SuppressFBWarnings("MDM_THREAD_FAIRNESS")
  public boolean tryLock() {
    if (jvmLock.tryLock()) {
      if (jvmLock.getHoldCount() > 1) {
        // reentrant
        return true;
      }
      try {
        fileLock = file.getChannel().tryLock();
        if (fileLock != null) {
          writeHolderInfo();
          return true;
        } else {
          jvmLock.unlock();
          return false;
        }
      } catch (IOException ex) {
        jvmLock.unlock();
        throw new LockRuntimeException(ex);
      } catch (RuntimeException ex) {
        jvmLock.unlock();
        throw ex;
      }
    } else {
      return false;
    }
  }

  @SuppressFBWarnings({"EXS_EXCEPTION_SOFTENING_HAS_CHECKED", "MDM_THREAD_YIELD"})
  @Override
  public boolean tryLock(final long time, final TimeUnit unit) throws InterruptedException {
    if (jvmLock.tryLock(time, unit)) {
      if (jvmLock.getHoldCount() >  1) {
        // reentered, we already have the file lock
        return true;
      }
      try {
        long waitTime = 0;
        long maxWaitTime = unit.toMillis(time);
        boolean interrupted = false;
        while (waitTime < maxWaitTime
                //CHECKSTYLE:OFF
                && (fileLock = file.getChannel().tryLock()) == null
                && !(interrupted = Thread.interrupted())) {
          //CHECKSTYLE:ON
          Thread.sleep(next(1000));
          waitTime++;
        }
        if (interrupted) {
          throw new InterruptedException();
        }
        if (fileLock != null) {
          writeHolderInfo();
          return true;
        } else {
          jvmLock.unlock();
          return false;
        }
      } catch (InterruptedException | RuntimeException ex) {
        jvmLock.unlock();
        throw ex;
      } catch (IOException ex) {
        jvmLock.unlock();
        throw new LockRuntimeException(ex);
      }
    } else {
      return false;
    }
  }

  @Override
  public void unlock() {
    try {
      if (jvmLock.getHoldCount() == 1) {
        fileLock.release();
      }
    } catch (IOException ex) {
      throw new LockRuntimeException(ex);
    } finally {
      jvmLock.unlock();
    }
  }

  @Override
  public Condition newCondition() {
    throw new UnsupportedOperationException();
  }

  /**
   * will release lock if owned, will not nothing if not owned.
   */
  @Override
  @WillClose
  public void close() {
    if (jvmLock.getHoldCount() > 0) {
      unlock();
    }
  }

  @Override
  protected void finalize() throws Throwable  {
    try (RandomAccessFile f = file) {
      super.finalize();
    }
  }

  private void writeHolderInfo() throws IOException {
    file.seek(0);
    byte[] data = getContextInfo().getBytes(Charsets.UTF_8);
    file.write(data);
    file.setLength(data.length);
    file.getChannel().force(true);
  }

  public static String getContextInfo() {
    return org.spf4j.base.Runtime.PROCESS_ID + ':' + Thread.currentThread().getName();
  }

  @Override
  public String toString() {
    return "FileBasedLock{" + "file=" + file + '}';
  }

  public int getLocalHoldCount() {
    return jvmLock.getHoldCount();
  }

  @SuppressFBWarnings("MDM_LOCK_ISLOCKED")
  public boolean isHeldByCurrentThread() {
    return jvmLock.isHeldByCurrentThread();
  }

}
