package cn.isuyu.dubbo.consumer.controller;

import cn.isuyu.dubbo.demo.common.service.SayService;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : niezl
 * @date : 2020/9/10
 */
@RestController
public class HelloController {

    @Reference(version = "1.0")
    private SayService sayService;

    @GetMapping(value = "hello")
    public String hello() {

        return sayService.say("Tom");
    }
}
