package com.im.openpush.utils;

import android.os.Handler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadUtil {

	/**
	 * 使用线程池管理子线程 线程池里线程个数为 3
	 */
	private static ExecutorService executorService = Executors.newFixedThreadPool(3);

	/**
	 * 子线程运行
	 * @param r
	 */
	public static void runOnBackThread(Runnable r) {
		executorService.execute(new Thread(r));
	}

	public static Handler handler = new Handler();

	/**
	 * 主线程运行
	 * @param r
	 */
	public static void runOnUIThread(Runnable r) {
		handler.post(r);
	}
}
