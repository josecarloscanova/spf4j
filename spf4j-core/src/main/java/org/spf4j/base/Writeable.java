package org.spf4j.base;

import java.io.IOException;
import javax.annotation.Nonnull;

/**
 * Interface for Objects that can write themselves to a appendable.
 * @author zoly
 */
public interface Writeable {

  void writeTo(@Nonnull Appendable appendable) throws IOException;

  default void writeTo(@Nonnull StringBuilder builder) {
    try {
      writeTo((Appendable) builder);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

}
