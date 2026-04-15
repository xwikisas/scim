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

import java.util.List;

import org.xwiki.model.reference.LocalDocumentReference;

/**
 * Shared constants.
 * @version $Id$
 * @since 0.0.1
 */
public final class Constants
{
    /**
     * Where the SCIM UI Code is found.
     */
    public static final List<String> SCIM_CODE_SPACE = List.of("SCIM", "Code");

    /**
     * Where the configuration document is found.
     */
    public static final LocalDocumentReference SCIM_CONF_REF = new LocalDocumentReference(SCIM_CODE_SPACE,
        "Configuration");

    /**
     * Where the configuration document class is found.
     */
    public static final LocalDocumentReference SCIM_CONF_CLASS_REF = new LocalDocumentReference(SCIM_CODE_SPACE,
        "ConfigurationClass");

    private Constants()
    {
        // ignore
    }
}
