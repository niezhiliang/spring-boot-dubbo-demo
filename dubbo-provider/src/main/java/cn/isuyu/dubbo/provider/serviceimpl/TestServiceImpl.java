package cn.isuyu.dubbo.provider.serviceimpl;

import cn.isuyu.dubbo.demo.common.service.TestService;
import org.apache.dubbo.config.annotation.Service;

/**
 * @author : niezl
 * @date : 2020/9/12
 */
@Service(version = "2.0")
public class TestServiceImpl implements TestService {
    @Override
    public String test() {
        return "hello test method";
    }
}
