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
import java.net.URISyntaxException;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeAll;

import org.junit.jupiter.api.TestInstance;
import org.xwiki.test.ui.TestUtils;

import static com.xwiki.scim.rest.Utils.getScimEndpointURL;
import static com.xwiki.scim.rest.Utils.setScimAdmins;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseSCIMRestIT
{
    protected URI baseScimURL;
    private XWikiUser scimAdmin;

    @BeforeAll
    void baseSetup(TestUtils setup) throws URISyntaxException
    {
        XWikiUser admin = scimAdmin();
        baseScimURL = new URI(getScimEndpointURL(setup).replace("http://",
                "http://" + admin.userName() + ':' + admin.password() + '@'));
        createUser(setup, admin);
        setScimAdmins(setup, admin);
    }

    XWikiUser scimAdmin()
    {
        if (scimAdmin == null) {
            String userName = this.getClass().getSimpleName() + "ScimAdmin";
            String password = "scimPassword";
            scimAdmin = new XWikiUser(userName, password);
        }
        return scimAdmin;
    }

    static void deletePage(TestUtils setup, String page)
    {
        setup.deletePage("XWiki", page);
    }

    static void createUser(TestUtils setup, XWikiUser user)
    {
        setup.createUser(user.userName(), user.password(), null);
    }
}
