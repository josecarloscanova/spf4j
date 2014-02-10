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
package org.spf4j.zel.vm;

import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * result cache for storing.
 *
 * @author zoly
 */
public interface ResultCache {

    void putPermanentResult(Program program, @Nonnull List<Object> params, @Nullable Object result);

    void putTransientResult(Program program, @Nonnull List<Object> params,  @Nullable Object result);

    @Nullable
    Object getResult(Program program, @Nonnull List<Object> params, Callable<Object> compute)
            throws ZExecutionException;
    
    Object NULL = new Object();
    
}
