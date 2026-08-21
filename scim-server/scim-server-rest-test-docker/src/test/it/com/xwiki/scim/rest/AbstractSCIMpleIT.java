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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;

import static com.xwiki.scim.rest.Utils.setScimAdmins;
import static com.xwiki.scim.rest.Utils.setUserNameCleaning;

@UITest(vnc = false)
//@UITest(vnc = false, servletEngine = org.xwiki.test.docker.junit5.servletengine.ServletEngine.EXTERNAL)
abstract class AbstractSCIMpleIT extends BaseSCIMRestIT
{
    protected abstract String auth();
    protected abstract URI baseScimURL();

    @Nested
    @UITest
    class ScimpleUsersIT extends org.apache.directory.scim.compliance.tests.UsersIT
    {
        @Override
        protected URI uri()
        {
            return baseScimURL();
        }

        @Override
        protected String authorization()
        {
            return auth();
        }
    }

    @Nested
    @UITest
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ScimpleGroupsIT extends org.apache.directory.scim.compliance.tests.GroupsIT
    {
        @BeforeAll
        void setup(TestUtils setup)
        {
            setUserNameCleaning(setup, false);
        }

        @Override
        protected URI uri()
        {
            return baseScimURL();
        }

        @Override
        protected String authorization()
        {
            return auth();
        }
    }

    @Nested
    @UITest
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ScimpleSchemasIT extends org.apache.directory.scim.compliance.tests.SchemasIT
    {
        @BeforeAll
        void setup(TestUtils setup)
        {
            setUserNameCleaning(setup, false);
        }

        @Override
        protected URI uri()
        {
            return baseScimURL();
        }

        @Override
        protected String authorization()
        {
            return auth();
        }
    }

    @Nested
    @UITest
    class ScimpleResourcesTypesIT extends org.apache.directory.scim.compliance.tests.ResourceTypesIT
    {
        @Override
        protected URI uri()
        {
            return baseScimURL();
        }

        @Override
        protected String authorization()
        {
            return auth();
        }
    }

    @Nested
    @UITest
    class ScimpleServiceProviderConfigIT extends org.apache.directory.scim.compliance.tests.ServiceProviderConfigIT
    {
        @Override
        protected URI uri()
        {
            return baseScimURL();
        }

        @Override
        protected String authorization()
        {
            return auth();
        }
    }
}
