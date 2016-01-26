/*
 * SonarLint Core Library
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.sonarlint.core.plugin;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import java.io.FileInputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.commons.codec.digest.DigestUtils;

public class LocalPluginIndexProvider implements PluginIndexProvider {

  private final List<URL> pluginUrls;

  public LocalPluginIndexProvider(List<URL> pluginUrls) {
    this.pluginUrls = pluginUrls;
  }

  @Override
  public List<PluginReference> references() {
    return Lists.transform(pluginUrls, new Function<URL, PluginReference>() {
      @Override
      public PluginReference apply(URL input) {
        return toPlugin(input);
      }

      private PluginReference toPlugin(URL input) {
        try {
          PluginReference ref = new PluginReference();
          Path plugin = Paths.get(input.toURI());
          try (FileInputStream fis = new FileInputStream(plugin.toFile())) {
            ref.setHash(DigestUtils.md5Hex(fis));
          }
          ref.setDownloadUrl(input);
          return ref;
        } catch (Exception e) {
          throw new IllegalStateException("Unable to load local plugins", e);
        }
      }
    });
  }
}
