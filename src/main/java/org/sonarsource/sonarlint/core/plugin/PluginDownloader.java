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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.sonar.api.internal.apachecommons.lang.StringUtils;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.api.utils.log.Profiler;
import org.sonarsource.sonarlint.core.container.PluginInfo;
import org.sonarsource.sonarlint.core.plugin.PluginIndexProvider.PluginReference;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;
import com.google.common.annotations.VisibleForTesting;

public class PluginDownloader {

  private static final Logger LOG = Loggers.get(PluginDownloader.class);

  private final PluginCache fileCache;
  private final PluginIndexProvider pluginIndexProvider;

  public PluginDownloader(PluginCache fileCache, PluginIndexProvider pluginIndexProvider) {
    this.fileCache = fileCache;
    this.pluginIndexProvider = pluginIndexProvider;
  }

  public Map<String, PluginInfo> installRemotes() {
    return loadPlugins(pluginIndexProvider.references());
  }

  private Map<String, PluginInfo> loadPlugins(List<PluginReference> pluginReferences) {
    Map<String, PluginInfo> infosByKey = new HashMap<>();

    Profiler profiler = Profiler.create(LOG).startDebug("Load plugins");

    for (PluginReference pluginReference : pluginReferences) {
      File jarFile = download(pluginReference);
      PluginInfo info = PluginInfo.create(jarFile);
      infosByKey.put(info.getKey(), info);
    }

    profiler.stopDebug();
    return infosByKey;
  }

  @VisibleForTesting
  File download(final PluginReference pluginReference) {
    final URL url = pluginReference.getDownloadUrl();
    try {
      return fileCache.get(StringUtils.substringAfterLast(url.getFile(), "/"), pluginReference.getHash(), new FileDownloader(url));
    } catch (Exception e) {
      throw new IllegalStateException("Fail to download plugin: " + url, e);
    }
  }

  private class FileDownloader implements PluginCache.Downloader {
    private URL url;

    FileDownloader(URL url) {
      this.url = url;
    }

    @Override
    public void download(String filename, File toFile) throws IOException {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Download plugin {} to {}", url, toFile);
      } else {
        LOG.info("Download {}", StringUtils.substringAfterLast(url.getFile(), "/"));
      }

      FileUtils.copyURLToFile(url, toFile);
    }
  }
}
