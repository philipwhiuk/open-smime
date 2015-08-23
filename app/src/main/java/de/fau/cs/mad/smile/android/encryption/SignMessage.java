package de.fau.cs.mad.smile.android.encryption;

import android.os.AsyncTask;
import android.util.Log;

import org.joda.time.DateTime;
import org.spongycastle.cms.SignerInfoGenerator;
import org.spongycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.spongycastle.operator.bc.BcDigestCalculatorProvider;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

import javax.mail.Address;
import javax.mail.Multipart;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;

public class SignMessage {
    private final KeyManagement keyManagement;

    public SignMessage() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        keyManagement = new KeyManagement();
    }

    public MimeMultipart sign(MimeBodyPart mimeBodyPart, Address signerAddress) {
        if(mimeBodyPart == null) {
            Log.e(SMileCrypto.LOG_TAG, "Could not sign, mimeBodyPart was null.");
            SMileCrypto.EXIT_STATUS = SMileCrypto.STATUS_INVALID_PARAMETER;
            return null;
        }

        if(signerAddress == null) {
            Log.e(SMileCrypto.LOG_TAG, "Could not sign, signerAddress was null.");
            SMileCrypto.EXIT_STATUS = SMileCrypto.STATUS_INVALID_PARAMETER;
            return null;
        }

        PrivateKey privateKey;
        X509Certificate certificate;

        try {
            ArrayList<KeyInfo> keyInfos = keyManagement.getKeyInfoByOwnAddress(signerAddress);
            if(keyInfos.size() == 0) {
                SMileCrypto.EXIT_STATUS = SMileCrypto.STATUS_NO_CERTIFICATE_FOUND;
                Log.e(SMileCrypto.LOG_TAG, "No certificate found for signer address: " + signerAddress);
                return null;
            }

            String alias = "";
            DateTime termination = new DateTime(0);

            for(KeyInfo keyInfo : keyInfos) { // use cert with longest validity
                DateTime terminationDate = keyInfo.termination_date;
                if(terminationDate.isAfter(termination)) {
                    alias = keyInfo.alias;
                    termination = terminationDate;
                }
            }

            privateKey = keyManagement.getPrivateKeyForAlias(alias, keyManagement.getPassphraseForAlias(alias));
            certificate = keyManagement.getCertificateForAlias(alias);
        } catch (Exception e) {
            Log.e(SMileCrypto.LOG_TAG, "Error getting info from KeyManagement: " + e.getMessage());
            SMileCrypto.EXIT_STATUS = SMileCrypto.STATUS_UNKNOWN_ERROR;
            return null;
        }

        return sign(mimeBodyPart, privateKey, certificate);
    }

    public Multipart sign(MimeBodyPart mimeBodyPart, String alias) throws KeyStoreException {
        if(mimeBodyPart == null) {
            Log.e(SMileCrypto.LOG_TAG, "Could not sign, mimeBodyPart was null.");
            SMileCrypto.EXIT_STATUS = SMileCrypto.STATUS_INVALID_PARAMETER;
            return null;
        }

        if(alias == null) {
            Log.e(SMileCrypto.LOG_TAG, "Could not sign, alias was null.");
            SMileCrypto.EXIT_STATUS = SMileCrypto.STATUS_INVALID_PARAMETER;
            return null;
        }

        String passphrase = keyManagement.getPassphraseForAlias(alias);
        PrivateKey privateKey = keyManagement.getPrivateKeyForAlias(alias, passphrase);
        X509Certificate certificate = keyManagement.getCertificateForAlias(alias);

        return sign(mimeBodyPart, privateKey, certificate);
    }

    private MimeMultipart sign(MimeBodyPart mimeBodyPart, PrivateKey privateKey, X509Certificate certificate) {
        if(mimeBodyPart == null) {
            Log.e(SMileCrypto.LOG_TAG, "Could not sign, mimeBodyPart was null.");
            SMileCrypto.EXIT_STATUS = SMileCrypto.STATUS_INVALID_PARAMETER;
            return null;
        }

        if(privateKey == null) {
            Log.e(SMileCrypto.LOG_TAG, "Could not sign, privateKey was null.");
            SMileCrypto.EXIT_STATUS = SMileCrypto.STATUS_INVALID_PARAMETER;
            return null;
        }

        if(certificate == null) {
            Log.e(SMileCrypto.LOG_TAG, "Could not sign, certificate was null.");
            SMileCrypto.EXIT_STATUS = SMileCrypto.STATUS_INVALID_PARAMETER;
            return null;
        }

        try {
            return new AsyncSign(mimeBodyPart, privateKey, certificate).execute().get();
        } catch (Exception e) {
            Log.e(SMileCrypto.LOG_TAG, "Exception in sign: " + e.getMessage());
            SMileCrypto.EXIT_STATUS = SMileCrypto.STATUS_ERROR_ASYNC_TASK;
            return null;
        }
    }

    /* Note: doing this is strongly not recommended as it means a recipient of the message will have to be able to read the signature to read the message.*/
    @Deprecated
    public MimeBodyPart signEncapsulated(MimeBodyPart mimeBodyPart, PrivateKey privateKey, X509Certificate certificate) {
        SMIMEToolkit smimeToolkit = new SMIMEToolkit(new BcDigestCalculatorProvider());
        MimeBodyPart signedMimeBodyPart = null;
        try {
            Log.d(SMileCrypto.LOG_TAG, "Sign mimeBodyPart.");

            SignerInfoGenerator signerInfoGenerator = new
                    JcaSimpleSignerInfoGeneratorBuilder().setProvider("SC").build("SHA256WITHRSA", privateKey, certificate);

            signedMimeBodyPart = smimeToolkit.signEncapsulated(mimeBodyPart, signerInfoGenerator);
        } catch (Exception e) {
            Log.e(SMileCrypto.LOG_TAG, "Error signing mimeBodyPart: " + e.getMessage());
        }

        return signedMimeBodyPart;
    }

    public Multipart signSynchronous(MimeBodyPart mimeBodyPart, String alias) {
        if(mimeBodyPart == null) {
            Log.e(SMileCrypto.LOG_TAG, "Could not sign, mimeBodyPart was null.");
            SMileCrypto.EXIT_STATUS = SMileCrypto.STATUS_INVALID_PARAMETER;
            return null;
        }
        if(alias == null) {
            Log.e(SMileCrypto.LOG_TAG, "Could not sign, alias was null.");
            SMileCrypto.EXIT_STATUS = SMileCrypto.STATUS_INVALID_PARAMETER;
            return null;
        }
        KeyManagement keyManagement;
        PrivateKey privateKey;
        X509Certificate certificate;
        try {
            keyManagement = new KeyManagement();
            privateKey = keyManagement.getPrivateKeyForAlias(alias, keyManagement.getPassphraseForAlias(alias));
            certificate = keyManagement.getCertificateForAlias(alias);
        } catch (Exception e) {
            Log.e(SMileCrypto.LOG_TAG, "Error getting info from KeyManagement: " + e.getMessage());
            SMileCrypto.EXIT_STATUS = SMileCrypto.STATUS_UNKNOWN_ERROR;
            return null;
        }
        return signSynchronous(mimeBodyPart, privateKey, certificate);
    }

    public Multipart signSynchronous(MimeBodyPart mimeBodyPart, Address signerAddress) {
        if(mimeBodyPart == null) {
            Log.e(SMileCrypto.LOG_TAG, "Could not sign, mimeBodyPart was null.");
            SMileCrypto.EXIT_STATUS = SMileCrypto.STATUS_INVALID_PARAMETER;
            return null;
        }
        if(signerAddress == null) {
            Log.e(SMileCrypto.LOG_TAG, "Could not sign, signerAddress was null.");
            SMileCrypto.EXIT_STATUS = SMileCrypto.STATUS_INVALID_PARAMETER;
            return null;
        }
        KeyManagement keyManagement;
        PrivateKey privateKey;
        X509Certificate certificate;
        try {
            keyManagement = new KeyManagement();
            ArrayList<KeyInfo> keyInfos = keyManagement.getKeyInfoByOwnAddress(signerAddress);
            if(keyInfos.size() == 0) {
                SMileCrypto.EXIT_STATUS = SMileCrypto.STATUS_NO_CERTIFICATE_FOUND;
                Log.e(SMileCrypto.LOG_TAG, "No certificate found for signer address: " + signerAddress);
                return null;
            }
            String alias = "";
            DateTime termination = new DateTime(0);
            for(KeyInfo keyInfo : keyInfos) { // use cert with longest validity
                DateTime terminationDate = keyInfo.termination_date;
                if(terminationDate.isAfter(termination)) {
                    alias = keyInfo.alias;
                    termination = terminationDate;
                }
            }
            privateKey = keyManagement.getPrivateKeyForAlias(alias, keyManagement.getPassphraseForAlias(alias));
            certificate = keyManagement.getCertificateForAlias(alias);
        } catch (Exception e) {
            Log.e(SMileCrypto.LOG_TAG, "Error getting info from KeyManagement: " + e.getMessage());
            SMileCrypto.EXIT_STATUS = SMileCrypto.STATUS_UNKNOWN_ERROR;
            return null;
        }
        return signSynchronous(mimeBodyPart, privateKey, certificate);
    }

    private MimeMultipart signSynchronous(MimeBodyPart mimeBodyPart, PrivateKey privateKey, X509Certificate certificate) {
        SMIMEToolkit smimeToolkit = new SMIMEToolkit(new BcDigestCalculatorProvider());
        MimeMultipart signedMimeMultipart = null;
        try {
            Log.d(SMileCrypto.LOG_TAG, "Sign mimeBodyPart.");

            SignerInfoGenerator signerInfoGenerator = new
                    JcaSimpleSignerInfoGeneratorBuilder().setProvider("SC").build("SHA256WITHRSA", privateKey, certificate);

            signedMimeMultipart = smimeToolkit.sign(mimeBodyPart, signerInfoGenerator);
        } catch (Exception e) {
            Log.e(SMileCrypto.LOG_TAG, "Error signing mimeBodyPart: " + e.getMessage());
            e.printStackTrace();
        }

        return signedMimeMultipart;
    }

    private class AsyncSign extends AsyncTask<Object, Void, MimeMultipart> {
        private final MimeBodyPart mimeBodyPart;
        private final PrivateKey privateKey;
        private final X509Certificate certificate;

        public AsyncSign(MimeBodyPart mimeBodyPart, PrivateKey privateKey, X509Certificate certificate) {
            this.mimeBodyPart = mimeBodyPart;
            this.privateKey = privateKey;
            this.certificate = certificate;
        }

        @Override
        protected MimeMultipart doInBackground(Object... params) {
            return signSynchronous(mimeBodyPart, privateKey, certificate);
        }
    }
}
