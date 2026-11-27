package dev.jdktrustmanager.model;

/** How a build tool download was checked before it was installed. */
public enum Verification {
    SIGNATURE, SHA512, SHA256, SHA1, MD5, NONE;

    /** SHA-1 and MD5 can be forged; installs checked only with them are flagged in the UI. */
    public boolean weak() {
        return this == SHA1 || this == MD5;
    }
}
