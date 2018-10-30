package com.sun.openjfx.tools.packager.mac;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class X509Certificates {

    /**
     * Equivalent to the following two OpenSSL invocations:
     *
     * <pre>{@code
     * openssl req -x509 -newkey rsa:2048 -sha256 -nodes -keyout {certPrefix + ".key"}
     * -out {certPrefix + ".pem"} -subj "/CN=Developer ID Application: Insecure Test Cert/OU=JavaFX Dev/O=Oracle/C=US"
     * -days 10 -addext "keyUsage=critical,digitalSignature" -addext "basicConstraints=critical,CA:false"
     * -addext "extendedKeyUsage=critical,codeSigning"
     * }</pre>
     *
     * and:
     *
     * <pre>{@code
     * openssl pkcs12 -export -nodes -info -out {certPrefix + ".pfx"}, -inkey {generated_above}
     * -in {generated_above} -password pass:1234
     * }</pre>
     */
    public static void generateTestCertificate(String certPrefix, Path certificateDirectory) {
        SecureRandom random = new SecureRandom();

        // -newkey rsa:2048
        KeyPairGenerator keyPairGenerator = null;
        try {
            keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        keyPairGenerator.initialize(2048, random);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        X500NameBuilder nameBuilder = new X500NameBuilder();
        // /CN=Developer ID Application: Insecure Test Cert/OU=JavaFX Dev/O=Oracle/C=US
        nameBuilder.addRDN(BCStyle.CN, "Developer ID Application: Insecure Test Cert");
        nameBuilder.addRDN(BCStyle.OU, "JavaFX Dev");
        nameBuilder.addRDN(BCStyle.O, "Oracle");
        nameBuilder.addRDN(BCStyle.C, "US");

        X500Name x500Name = nameBuilder.build();

        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        X509v3CertificateBuilder x509v3CertificateBuilder = new X509v3CertificateBuilder(x500Name, serial,
                new Date(System.currentTimeMillis()),
                new Date(System.currentTimeMillis() + 10 * 24 * 60 * 60 * 1000), // -days 10
                x500Name, SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded()));
        try {
            // basicConstraints=critical,CA:false
            x509v3CertificateBuilder.addExtension(Extension.basicConstraints, true,
                    new BasicConstraints(false));
            // keyUsage=critical,digitalSignature
            x509v3CertificateBuilder.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.digitalSignature));
            // extendedKeyUsage=critical,codeSigning
            x509v3CertificateBuilder.addExtension(Extension.extendedKeyUsage, true, new
                    ExtendedKeyUsage(KeyPurposeId.id_kp_codeSigning));
        } catch (CertIOException e) {
            e.printStackTrace();
        }
        try {
            // Write X509 certificate to {certPrefix + ".pem"}
            ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
            X509CertificateHolder x509CertificateHolder = x509v3CertificateBuilder.build(contentSigner);
            FileWriter certificateWriter = new FileWriter(certificateDirectory.resolve(certPrefix + ".pem").toFile());
            JcaPEMWriter pemWriter = new JcaPEMWriter(certificateWriter);
            X509Certificate x509Certificate = new JcaX509CertificateConverter().getCertificate(x509CertificateHolder);
            pemWriter.writeObject(x509Certificate);
            pemWriter.flush();
            pemWriter.close();

            // Write private key to {certPrefix + ".key"}
            pemWriter = new JcaPEMWriter(new FileWriter(certificateDirectory.resolve(certPrefix + ".key").toFile()));
            pemWriter.writeObject(keyPair.getPrivate());
            pemWriter.flush();
            pemWriter.close();

            // Write X509 certificate and private key to PKCS12 keystore {certPrefix + ".pfx"}
            FileOutputStream fileOutputStream = new FileOutputStream(
                    certificateDirectory.resolve(certPrefix + ".pfx").toFile());
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null);
            keyStore.setKeyEntry("alias", keyPair.getPrivate().getEncoded(), new X509Certificate[] { x509Certificate });
            keyStore.store(fileOutputStream, "1234".toCharArray());
            fileOutputStream.flush();
            fileOutputStream.close();
        } catch (OperatorCreationException | IOException | CertificateException | KeyStoreException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

}
