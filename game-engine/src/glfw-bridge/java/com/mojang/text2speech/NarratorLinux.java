package com.mojang.text2speech;

public class NarratorLinux implements Narrator {
    public NarratorLinux() throws Narrator.InitializeException {
        // Clean Android no-op narrator implementation (avoids loading desktop libflite.so)
    }

    @Override
    public void say(String msg, boolean interrupt, float volume) {
    }

    @Override
    public void clear() {
    }

    @Override
    public boolean active() {
        return false;
    }

    @Override
    public void destroy() {
    }
}
