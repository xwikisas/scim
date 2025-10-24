/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xwiki.scim.server.internal;

import java.lang.reflect.Field;

import org.apache.directory.scim.server.rest.BaseResourceTypeResourceImpl;
import org.xwiki.component.phase.InitializationException;

/**
 * Internal utilities.
 * @since 0.0.1
 * @version $Id$
 */
public final class Utils
{
    private Utils()
    {
        // ignore
    }

    /**
     * Set the given field to the given value on the given object using reflection.
     * @param impl the object on which to set the value
     * @param fieldName the field to set
     * @param value the value to set
     * @throws InitializationException is something wrong happens
     */
    public static void setField(Object impl, String fieldName, Object value) throws InitializationException
    {
        try {
            Class<?> c = impl instanceof BaseResourceTypeResourceImpl
                ? BaseResourceTypeResourceImpl.class
                : impl.getClass();
            Field f = c.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(impl, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new InitializationException("Failed to initialize BaseResourceTypeResourceImpl", e);
        }
    }

}
