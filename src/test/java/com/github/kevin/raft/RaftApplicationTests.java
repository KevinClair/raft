package com.github.kevin.raft;

import org.junit.jupiter.api.Test;

class RaftApplicationTests {

	@Test
	void contextLoads() throws InterruptedException {
		InstanceTest instance = InstanceTest.getInstance();
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			System.out.println("进入Shutdown");
			synchronized (instance) {
				System.out.println("唤醒线程");
				instance.notifyAll();
			}
		}));

		synchronized (instance) {
			System.out.println("线程等待");
			instance.wait(5000);
			System.out.println("线程被唤醒");
			Thread.sleep(3000);
		}

	}

}
