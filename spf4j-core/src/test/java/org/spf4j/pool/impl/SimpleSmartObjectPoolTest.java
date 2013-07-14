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
package org.spf4j.pool.impl;

import org.spf4j.pool.Disposable;
import org.spf4j.pool.ObjectBorower;
import org.spf4j.pool.ObjectCreationException;
import org.spf4j.pool.ObjectPool;
import java.util.Collection;
import org.junit.Test;

/**
 *
 * @author zoly
 */
public final class SimpleSmartObjectPoolTest implements ObjectBorower<SimpleSmartObjectPoolTest.TestObject> {

    private TestObject borowedObject = null;
    private SimpleSmartObjectPool<TestObject> instance;

    public SimpleSmartObjectPoolTest() throws ObjectCreationException {
        instance = new SimpleSmartObjectPool(2, 10, new ObjectPool.Factory<TestObject>() {
            @Override
            public TestObject create() {
                System.out.println("Creating Object");
                return new TestObject("Object");
            }

            @Override
            public void dispose(final TestObject object) {
                System.out.println("Disposing Object");
                object.dispose();
            }

            @Override
            public Exception validate(final TestObject object, final Exception e) {
                return new UnsupportedOperationException("Not supported yet.");
            }
        }, 10000, true);
    }

    @Override
    public TestObject requestReturnObject() {
        if (borowedObject != null) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
            instance.returnObject(borowedObject, this);
        }
        return null;
    }

    @Override
    public TestObject returnObjectIfNotInUse() {
        return borowedObject;
    }

    @Override
    public boolean scan(final ScanHandler<TestObject> handler) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Collection<TestObject> returnObjectsIfNotNeeded() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Collection<TestObject> returnObjectsIfNotInUse() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public static final class TestObject implements Disposable {

        private boolean disposed = false;
        private final String data;

        public TestObject(final String data) {
            this.data = data;
        }

        public String getData() {
            if (!disposed) {
                return data;
            } else {
                throw new RuntimeException(data + " is already disposed");
            }
        }

        @Override
        public void dispose() {
            disposed = true;
        }
    }

    /**
     * Test of borrowObject method, of class SimpleSmartObjectPool.
     */
    @Test
    public void testPool() throws Exception {
        System.out.println("borrowObject");
        borowedObject = instance.borrowObject(this);
        instance.returnObject(borowedObject, this);
        instance.dispose();
    }
}