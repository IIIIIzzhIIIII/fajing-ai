package com.fajing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 模拟 AI 生成：8 秒后返回占位图
 * TODO: 接入真实 AI 出图服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationProcessor {

    private final GenerationService generationService;

    @Async
    public void process(Long taskId) {
        int[] steps = {15, 40, 65, 90};
        try {
            for (int step : steps) {
                Thread.sleep(2000);
                generationService.updateProgress(taskId, step);
            }
            generationService.finish(taskId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("生成任务被中断 taskId={}", taskId);
        } catch (Exception e) {
            log.error("生成任务异常 taskId={}", taskId, e);
            generationService.updateProgress(taskId, 0);
        }
    }
}
