
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.util.CancelException;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;


public class SelectClassTest {
    public void test(String dict)  {
        String project_target = "./ClassicAutomatedTesting/"+dict+"/target";
        String change_info = "./ClassicAutomatedTesting/"+dict+"/data/change_info.txt";
        String dataPath = "./ClassicAutomatedTesting/"+dict+"/data";
        TestTool.test(project_target,change_info,dataPath,"c");
    }

    @Test
    public void test0()  {
        String dict = "0-CMD";
        test(dict);
    }

    @Test
    public void test1()  {
        String dict = "1-ALU";
        test(dict);
    }

    @Test
    public void test2()  {
        String dict = "2-DataLog";
        test(dict);
    }

    @Test
    public void test3()  {
        String dict = "3-BinaryHeap";
        test(dict);
    }

    @Test
    public void test4()  {
        String dict = "4-NextDay";
        test(dict);
    }

    @Test
    public void test5()  {
        String dict = "5-MoreTriangle";
        test(dict);
    }
}
