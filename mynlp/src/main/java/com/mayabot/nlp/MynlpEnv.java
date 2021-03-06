/*
 * Copyright 2018 mayabot.com authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mayabot.nlp;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.mayabot.nlp.logging.InternalLogger;
import com.mayabot.nlp.logging.InternalLoggerFactory;
import com.mayabot.nlp.resources.NlpResource;
import com.mayabot.nlp.resources.NlpResourceFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.Charset;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List;

/**
 * Mynlp运行环境。
 * 负责数据目录，缓存、资源加载、Settings等
 *
 * @author jimichan
 */
public class MynlpEnv {

    public static InternalLogger logger = InternalLoggerFactory.getInstance(MynlpEnv.class);

    /**
     * 数据目录
     */
    private File dataDir;

    /**
     * 缓存文件目录
     */
    private File cacheDir;

    private List<NlpResourceFactory> resourceFactory;

    private Settings settings;

    MynlpEnv(File dataDir, File cacheDir, List<NlpResourceFactory> resourceFactory, Settings settings) {
        this.dataDir = dataDir;
        this.cacheDir = cacheDir;
        this.resourceFactory = ImmutableList.copyOf(resourceFactory);
        this.settings = settings;
    }


    public Settings getSettings() {
        return settings;
    }

    public void set(String key, String value) {
        settings.put(key, value);
    }

    public @Nullable
    String get(String setting) {
        return settings.get(setting);
    }

    public String get(String setting, @NotNull String defaultValue) {
        return settings.get(setting, defaultValue);
    }

    public <T> T get(SettingItem<T> setting) {
        return settings.get(setting);
    }

    /**
     * 加载资源
     *
     * @param resourcePath 资源路径名称 dict/abc.dict
     * @return NlpResource
     */
    @Nullable
    public NlpResource loadResource(String resourcePath) {
        return this.loadResource(resourcePath, Charsets.UTF_8);
    }

    /**
     * 加载资源
     *
     * @param resourcePath 资源路径名称 dict/abc.dict
     * @param charset      字符集
     * @return NlpResource
     */
    public @NotNull
    NlpResource loadResource(String resourcePath, Charset charset) {
        // TODO wiki path need
        if (resourcePath == null || resourcePath.trim().isEmpty()) {
            throw new RuntimeException("resourcePath is null");
        }

        return AccessController.doPrivileged((PrivilegedAction<NlpResource>) () -> {
            String wiki = "https://github.com/mayabot/mynlp/wiki/resources";
            NlpResource resource = getNlpResource(resourcePath, charset);

            if (resource == null) {
                throw new RuntimeException(
                        "Not Found Resource " + resourcePath
                );
            }
            return resource;
        });

    }


    /**
     * 计算资源的hash值。
     *
     * @param resourceName
     * @return hash
     */
    public @Nullable
    String hashResource(String resourceName) {

        NlpResource r1 = tryLoadResource(resourceName, Charsets.UTF_8);
        if (r1 != null) {
            return r1.hash();
        }
        return null;
    }


    public @Nullable
    NlpResource tryLoadResource(String resourcePath, Charset charset) {
        return AccessController.doPrivileged((PrivilegedAction<NlpResource>) () -> {
            if (resourcePath == null || resourcePath.trim().isEmpty()) {
                return null;
            }

            return getNlpResource(resourcePath, charset);
        });
    }

    public @Nullable
    NlpResource tryLoadResource(String resourcePath) {
        return this.tryLoadResource(resourcePath, Charsets.UTF_8);
    }

    public @Nullable
    NlpResource tryLoadResource(SettingItem<String> resourceNameSetting) {
        return this.tryLoadResource(settings.get(resourceNameSetting), Charsets.UTF_8);
    }

    private synchronized NlpResource getNlpResource(String resourceName, Charset charset) {
        NlpResource resource = null;
        long t1 = System.currentTimeMillis();
        for (NlpResourceFactory factory : resourceFactory) {
            resource = factory.load(resourceName, charset);
            if (resource != null) {
                String string = resource.toString();
                if (string.length() >= 100) {
                    string = "../.." + string.substring(string.length() - 60);
                }
                long t2 = System.currentTimeMillis();
                logger.info("load resource {} ,use time {} ms", string, t2 - t1);
                break;
            }
        }
        return resource;
    }


    public File getDataDir() {
        return dataDir;
    }


    public File getCacheDir() {
        return cacheDir;
    }

//
//    /**
//     * 从url地址下载jar文件，保存到data目录下
//     */
//    public synchronized File download(String fileName) {
//        return AccessController.doPrivileged((PrivilegedAction<File>) () -> {
//
//            File file = new File(dataDir, fileName);
//
//            if (file.exists()) {
//                return file;
//            }
//
//            try {
//                String url = downloadBaseUrl + fileName;
//
//                DownloadUtils.download(url, file);
//
//                System.out.println("Downloaded " + fileName + " , save to " + file);
//
//                if (file.exists()) {
//                    return file;
//                }
//
//            } catch (Exception e) {
//                System.err.println("Download " + (downloadBaseUrl + fileName) + " error!!!\n");
//                e.printStackTrace();
//
//                file.delete();
//                //下载失败 退出系统
//                //System.exit(0);
//            }
//
//            return null;
//        });
//
//    }
}