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

package com.xwiki.scim.rest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import org.xwiki.test.docker.junit5.UITest;

@UITest(vnc = false)
//@UITest(vnc = false, servletEngine = org.xwiki.test.docker.junit5.servletengine.ServletEngine.EXTERNAL)
class AllIT
{
    @Nested
    class NestedScimRestIT extends ScimRestIT { }

    @Nested
    @DisplayName("Tests from Apache Directory SCIMple")
    class NestedSCIMpleIT extends AbstractSCIMpleIT
    {
        private String auth;

        @Override
        protected String auth()
        {
            if (auth == null) {
                XWikiUser admin = scimAdmin();
                String authStr = "XWiki." + admin.userName() + ':' + admin.password();
                auth = "Basic " + Base64.getEncoder().encodeToString(authStr.getBytes(StandardCharsets.UTF_8));
            }
            return auth;
        }

        @Override
        protected URI baseScimURL()
        {
            return baseScimURL;
        }
    }
}
