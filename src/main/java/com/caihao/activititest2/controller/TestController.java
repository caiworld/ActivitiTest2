package com.caihao.activititest2.controller;

import com.caihao.activititest2.mapper.TestMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * create by caihao on 2019/12/31
 */
@Controller
@RequestMapping("/test")
public class TestController {

    @Autowired
    private TestMapper testMapper;

    @ResponseBody
    @RequestMapping("/hello")
    public String hello() {
        return "hello world";
    }

    @ResponseBody
    @RequestMapping("/test")
    public String testSql() {
        System.out.println(testMapper.selectCount());
        return "test success";
    }
}
