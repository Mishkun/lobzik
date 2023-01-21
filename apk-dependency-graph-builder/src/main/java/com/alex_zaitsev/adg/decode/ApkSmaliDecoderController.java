package com.alex_zaitsev.adg.decode;

import java.io.IOException;

public class ApkSmaliDecoderController {
    // TODO: Change the default API version to the current version of
    // the APK, to be according to the APK version.

    public static void decode(
            final String apkFilePath,
            final String outDirPath,
            final int apiVersion
    ) {
        try {
            new ApkSmaliDecoder(apkFilePath, outDirPath, apiVersion).decode();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }
}
