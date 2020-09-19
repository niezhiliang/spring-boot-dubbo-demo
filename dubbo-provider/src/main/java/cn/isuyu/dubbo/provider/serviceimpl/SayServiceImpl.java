package cn.isuyu.dubbo.provider.serviceimpl;

import cn.isuyu.dubbo.demo.common.service.SayService;
import org.apache.dubbo.config.annotation.Service;

/**
 * @author : niezl
 * @date : 2020/9/10
 */
@Service(version = "1.0")
public class SayServiceImpl implements SayService {
    @Override
    public String say(String name) {
        System.out.println(name + " say: hello");
        return name + " say: hello";
    }
}
