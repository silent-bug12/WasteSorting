package com.xingwang.util;

import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
public class AudioConverter {

    public static byte[] convertToPcm16kHz1Channel(byte[] audioData) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
            AudioInputStream sourceStream = AudioSystem.getAudioInputStream(bais);
            
            AudioFormat sourceFormat = sourceStream.getFormat();
            log.info("源音频格式: {}", sourceFormat);
            
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    16000,
                    16,
                    1,
                    2,
                    16000,
                    false
            );
            
            AudioInputStream pcmStream = AudioSystem.getAudioInputStream(targetFormat, sourceStream);
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            
            while ((bytesRead = pcmStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            
            sourceStream.close();
            pcmStream.close();
            
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("音频格式转换失败，返回原始数据: {}", e.getMessage());
            return audioData;
        }
    }

    public static byte[] extractAudioData(byte[] webmData) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(webmData);
            return extractFromWebm(bais);
        } catch (Exception e) {
            log.warn("无法从webm提取音频: {}", e.getMessage());
            return webmData;
        }
    }

    private static byte[] extractFromWebm(InputStream inputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        
        return baos.toByteArray();
    }
}
