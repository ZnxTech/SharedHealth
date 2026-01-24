package com.znxtech.sharedhealth;

import java.util.concurrent.locks.ReentrantLock;
 
public class LockedFloat {
    private final ReentrantLock lock = new ReentrantLock();
    private float value;
 
    public LockedFloat(float value) {
        this.value = value;
    }
 
    public float get() {
        this.lock.lock();
        try {
            return this.value;
        } finally {
            this.lock.unlock(); 
        }
    }
 
    public void set(float value) {
        this.lock.lock();
        try {
            this.value = value;
        } finally {
            this.lock.unlock();
        }
    }

    public float change(float delta) {
        this.lock.lock();
        try {
            this.value += delta;
            return this.value;
        } finally {
            this.lock.unlock();
        }
    }
}