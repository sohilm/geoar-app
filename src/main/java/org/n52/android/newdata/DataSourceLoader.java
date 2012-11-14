/**
 * Copyright 2012 52�North Initiative for Geospatial Open Source Software GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.n52.android.newdata;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.n52.android.GeoARApplication;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;

/**
 * 
 */
public class DataSourceLoader {

	public interface OnDataSourcesChangeListener {
		void onDataSourcesChange();
	}

	private static FilenameFilter pluginFilenameFilter = new FilenameFilter() {
		@Override
		public boolean accept(File dir, String fileName) {
			return fileName.endsWith(".apk") || fileName.endsWith(".zip")
					|| fileName.endsWith(".jar");
		}
	};

	private static final String PLUGIN_PATH = Environment
			.getExternalStorageDirectory() + "/GeoAR/";
	private static CheckList<DataSourceHolder> dataSources = new CheckList<DataSourceHolder>();
	private static Set<OnDataSourcesChangeListener> dataSourcesChangeListeners = new HashSet<OnDataSourcesChangeListener>();

	static {
		loadPlugins();
	}

	public static void reloadPlugins() {
		dataSources.clear();
		loadPlugins();
		for (OnDataSourcesChangeListener listener : dataSourcesChangeListeners) {
			listener.onDataSourcesChange();
		}
	}

	public static CheckList<DataSourceHolder> getDataSources() {
		return dataSources;
	}

	public static void addOnAvailableDataSourcesUpdateListener(
			OnDataSourcesChangeListener listener) {
		dataSourcesChangeListeners.add(listener);
	}

	public static void removeOnAvailableDataSourcesUpdateListener(
			OnDataSourcesChangeListener listener) {
		dataSourcesChangeListeners.remove(listener);
	}

	private static void loadPlugins() {

		String[] apksInDirectory = new File(PLUGIN_PATH)
				.list(pluginFilenameFilter);

		if (apksInDirectory.length == 0)
			return;

		try {
			for (String pluginFileName : apksInDirectory) {
				String pluginBaseFileName = pluginFileName.substring(0,
						pluginFileName.lastIndexOf("."));
				String pluginPath = PLUGIN_PATH + pluginFileName;
				// Check if the Plugin exists
				if (!new File(pluginPath).exists())
					throw new FileNotFoundException("Directory not found: "
							+ pluginPath);

				File tmpDir = GeoARApplication.applicationContext.getDir(
						pluginBaseFileName, 0);

				Enumeration<String> entries = DexFile.loadDex(
						pluginPath,
						tmpDir.getAbsolutePath() + "/" + pluginBaseFileName
								+ ".dex", 0).entries();
				// Path for optimized dex equals path which will be used by
				// dexClassLoader

				// create separate ClassLoader for each plugin
				DexClassLoader dexClassLoader = new DexClassLoader(pluginPath,
						tmpDir.getAbsolutePath(), null,
						GeoARApplication.applicationContext.getClassLoader());

				while (entries.hasMoreElements()) {
					// Check each classname for annotations
					String entry = entries.nextElement();

					Class<?> entryClass = dexClassLoader.loadClass(entry);
					if (entryClass
							.isAnnotationPresent(Annotations.DataSource.class)) {
						// Class is a annotated as datasource
						if (org.n52.android.newdata.DataSource.class
								.isAssignableFrom(entryClass)) {
							// Class is a datasource
							@SuppressWarnings("unchecked")
							DataSourceHolder dataSourceHolder = new DataSourceHolder(
									(Class<? extends DataSource<? super Filter>>) entryClass);
							dataSources.add(dataSourceHolder);
						} else {
							Log.e("GeoAR",
									"Datasource "
											+ entryClass.getSimpleName()
											+ " is not implementing DataSource interface");
							// TODO handle error
						}
					}
				}

			}
			// TODO Handle exceptions
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

	}
}
