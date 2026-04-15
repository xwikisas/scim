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
package com.xwiki.scim.server.internal.providers;

import java.util.ArrayList;
import java.util.Collection;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.apache.directory.scim.server.configuration.ServerConfiguration;
import org.apache.directory.scim.spec.schema.ServiceProviderConfiguration.AuthenticationSchema;
import org.apache.directory.scim.spec.schema.ServiceProviderConfiguration.AuthenticationSchema.Type;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;

import com.xwiki.scim.server.internal.ScimConfiguration;

/**
 * SCIM ServerConfiguration provider.
 * @version $Id$
 * @since 0.0.1
 */
@Component
@Singleton
public class ServerConfigurationProvider implements Provider<ServerConfiguration>
{
    private ServerConfiguration s;

    @Inject
    private ScimConfiguration scimConfiguration;

    @Inject
    private Logger logger;

    @Override
    public ServerConfiguration get()
    {
        if (s == null) {
            s = new ServerConfiguration();
        }

        Collection<String> advertisedAuthSchemes = scimConfiguration.getAdvertisedAuthSchemes();
        if (advertisedAuthSchemes != null) {
            removeAllAuthSchemes();
            for (String scheme : advertisedAuthSchemes) {
                if (Type.OAUTH.toString().equals(scheme)) {
                    s.addAuthenticationSchema(AuthenticationSchema.oauth());
                } else if (Type.OAUTH2.toString().equals(scheme)) {
                    s.addAuthenticationSchema(AuthenticationSchema.oauth2());
                } else if (Type.OAUTH_BEARER.toString().equals(scheme)) {
                    s.addAuthenticationSchema(AuthenticationSchema.oauth2());
                } else if (Type.HTTP_BASIC.toString().equals(scheme)) {
                    s.addAuthenticationSchema(AuthenticationSchema.httpBasic());
                } else if (Type.HTTP_DIGEST.toString().equals(scheme)) {
                    s.addAuthenticationSchema(AuthenticationSchema.httpDigest());
                } else {
                    logger.warn("Unknown authentication scheme [{}], ignoring", scheme);
                }
            }
        }

        s.setSupportsChangePassword(true);

        return s;
    }

    private void removeAllAuthSchemes()
    {
        for (AuthenticationSchema as : new ArrayList<>(s.getAuthenticationSchemas())) {
            s.removeAuthenticationSchema(as);
        }
    }
}
