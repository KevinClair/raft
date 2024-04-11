package com.github.kevin.raft;

public class InstanceTest {

    public static InstanceTest getInstance() {
        return InstanceTestHolder.instance;
    }

    private static class InstanceTestHolder {
        private static InstanceTest instance = new InstanceTest();
    }
}
