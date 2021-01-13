/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.openjfx.tools.packager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;

import javafx.css.Stylesheet;

import com.sun.openjfx.tools.packager.JarSignature.InputStreamSource;
import com.sun.openjfx.tools.packager.bundlers.BundleParams;
import com.sun.openjfx.tools.packager.bundlers.Bundler.BundleType;

public class PackagerLib {
    public static final String JAVAFX_VERSION = System.getProperty("java.version");

    public void generateDeploymentPackages(DeployParams deployParams) throws PackagerException {
        if (deployParams == null) {
            throw new IllegalArgumentException("Parameters must not be null.");
        }

        try {
            BundleParams bp = deployParams.getBundleParams();

            if (bp != null) {
                // Generate disk images.
                // Generate installers.
                // A specific output format, just generate that.
                if (deployParams.getBundleType() == BundleType.NATIVE) {
                    generateNativeBundles(deployParams.outdir, bp.getBundleParamsAsMap(),
                            BundleType.IMAGE.toString(), deployParams.getTargetFormat());
                    generateNativeBundles(deployParams.outdir, bp.getBundleParamsAsMap(),
                            BundleType.INSTALLER.toString(), deployParams.getTargetFormat());
                } else {
                    generateNativeBundles(deployParams.outdir, bp.getBundleParamsAsMap(),
                            deployParams.getBundleType().toString(), deployParams.getTargetFormat());
                }
            }
        } catch (PackagerException ex) {
            ex.printStackTrace();
            throw ex;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new PackagerException(ex, "Error: deploy failed", ex.getMessage());
        }

    }

    private void generateNativeBundles(File outdir, Map<String, ? super Object> params,
                                       String bundleType, String bundleFormat) throws PackagerException {
        for (com.sun.openjfx.tools.packager.Bundler bundler :
                Bundlers.createBundlersInstance().getBundlers(bundleType)) {
            // if they specify the bundle format, require we match the ID
            if (bundleFormat != null && !bundleFormat.equalsIgnoreCase(bundler.getID())) {
                continue;
            }

            Map<String, ? super Object> localParams = new HashMap<>(params);
            try {
                if (bundler.validate(localParams)) {
                    File result = bundler.execute(localParams, outdir);
                    bundler.cleanup(localParams);
                    if (result == null) {
                        throw new PackagerException("Error: Bundler \"{1}\" ({0}) failed to produce a bundle.",
                                bundler.getID(), bundler.getName());
                    }
                }
            } catch (UnsupportedPlatformException e) {
                Log.debug(MessageFormat.format(
                        "Bundler {0} skipped because the bundler does not support bundling on this platform.",
                        bundler.getName()));
            } catch (ConfigException e) {
                Log.debug(e);
                if (e.getAdvice() != null) {
                    Log.info(MessageFormat.format(
                            "Bundler {0} skipped because of a configuration problem: {1}\n  Advice to fix: {2}\n",
                            bundler.getName(), e.getMessage(), e.getAdvice()));
                } else {
                    Log.info(MessageFormat.format("Bundler {0} skipped because of a configuration problem: {1}",
                            bundler.getName(), e.getMessage()));
                }
            } catch (RuntimeException re) {
                re.printStackTrace();
                Log.info(MessageFormat.format("Bundler {0} failed because of {1}", bundler.getName(), re.toString()));
                Log.debug(re);
            }
        }
    }

    public void generateBSS(CreateBSSParams params) throws PackagerException {
        if (params == null) {
            throw new IllegalArgumentException("Parameters must not be null.");
        }
        createBinaryCss(params.resources, params.outdir);
    }

    public void signJar(SignJarParams params) throws PackagerException {
        try {
            JarSignature signature = retrieveSignature(params);

            for (PackagerResource pr : params.resources) {
                signFile(pr, signature, params.outdir, params.verbose);
            }

        } catch (Exception ex) {
            Log.verbose(ex);
            throw new PackagerException("Error: Signing failed", ex);
        }

    }

    private JarSignature retrieveSignature(SignJarParams params) throws KeyStoreException,
            NoSuchAlgorithmException, UnrecoverableKeyException, IOException,
            CertificateException, InvalidKeyException {
        if (params.keyPass == null) {
            params.keyPass = params.storePass;
        }

        if (params.keyStore == null) {
            throw new IOException("No keystore specified");
        }

        if (params.storePass == null) {
            throw new IOException("No store password specified");
        }

        if (params.storeType == null) {
            throw new IOException("No store type is specified");
        }

        KeyStore store = KeyStore.getInstance(params.storeType);
        store.load(new FileInputStream(params.keyStore), params.storePass.toCharArray());

        Certificate[] chain = store.getCertificateChain(params.alias);
        if (chain == null) {
            throw new IllegalArgumentException("given alias: " + params.alias +
                    " has no certificate chain for keystore: " + params.keyStore);
        }

        X509Certificate[] certChain = new X509Certificate[chain.length];
        for (int i = 0; i < chain.length; i++) {
            certChain[i] = (X509Certificate) chain[i];
        }

        PrivateKey privateKey = (PrivateKey)
                store.getKey(params.alias, params.keyPass.toCharArray());

        return JarSignature.create(privateKey, certChain);
    }

    private void signFile(PackagerResource pr, JarSignature signature, File outdir, boolean verbose)
            throws NoSuchAlgorithmException, IOException, SignatureException {
        if (pr.getFile().isDirectory()) {
            File[] children = pr.getFile().listFiles();
            if (children != null) {
                for (File innerFile : children) {
                    signFile(new PackagerResource(
                            pr.getBaseDir(), innerFile), signature, outdir, verbose);
                }
            }
        } else {
            File jar = pr.getFile();
            File parent = jar.getParentFile();
            String name = "bsigned_" + jar.getName();
            File signedJar = new File(parent, name);

            Log.info("Signing (BLOB) " + jar.getPath());

            signAsBLOB(jar, signedJar, signature);

            File destJar;
            if (outdir != null) {
                destJar = new File(outdir, pr.getRelativePath());
            } else {
                // in-place
                jar.delete();
                destJar = jar;
            }
            destJar.delete();
            destJar.getParentFile().mkdirs();
            signedJar.renameTo(destJar);
            if (verbose) {
                Log.info("Signed as " + destJar.getPath());
            }
        }
    }

    private void signAsBLOB(final File jar, File signedJar, JarSignature signature)
            throws IOException, NoSuchAlgorithmException, SignatureException {
        if (signature == null) {
            throw new IllegalStateException("Should retrieve signature first");
        }

        InputStreamSource in = () -> new FileInputStream(jar);
        if (!signedJar.isFile()) {
            signedJar.createNewFile();
        }
        FileOutputStream fos = new FileOutputStream(signedJar);
        signature.signJarAsBLOB(in, new ZipOutputStream(fos));
    }

    private void createBinaryCss(List<PackagerResource> cssResources, File outdir) throws PackagerException {
        for (PackagerResource cssRes : cssResources) {
            String relPath = cssRes.getRelativePath();
            createBinaryCss(cssRes.getFile(), outdir, relPath);
        }
    }

    private void createBinaryCss(File f, File outdir, String relPath) throws PackagerException {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File innerFile : children) {
                    createBinaryCss(innerFile, outdir, relPath + '/' + innerFile.getName());
                }
            }
        } else if (f.getName().endsWith(".css")) {
            String cssFileName = f.getAbsolutePath();
            String bssFileName = new File(outdir.getAbsolutePath(),
                    replaceExtensionByBSS(relPath))
                    .getAbsolutePath();
            createBinaryCss(cssFileName, bssFileName);
        }
    }

    private void createBinaryCss(String cssFile, String binCssFile) throws PackagerException {
        String ofname = binCssFile != null ? binCssFile : replaceExtensionByBSS(cssFile);

        // create parent directories
        File of = new File(ofname);
        File parentFile = of.getParentFile();
        if (parentFile != null) {
            parentFile.mkdirs();
        }
        try {
            Stylesheet.convertToBinary(new File(cssFile), of);
        } catch (IOException e) {
            throw new PackagerException(e, "Error: Conversion of CSS file to binary form failed for " +
                    "file: {0}, reason: {1}", cssFile, e.getMessage());
        }
    }

    private static String replaceExtensionByBSS(String cssName) {
        return cssName.substring(0, cssName.lastIndexOf(".") + 1).concat("bss");
    }

}
