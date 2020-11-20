import org.junit.Assert;
import org.junit.Test;

public class GenerateDot {
    public void test(String dict)  {
        String project_target = "./ClassicAutomatedTesting/"+dict+"/target";
        try{
            Selection.generateDot(project_target,dict);
        }catch (Exception e){
            e.printStackTrace();
            Assert.fail();
        }
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
