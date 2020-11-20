import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.util.CancelException;
import org.junit.Assert;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class TestTool {
    public static void test(String project_target,String change_info,String dataPath,String arg) {
        try{
            ArrayList<String> expect = null;
            ArrayList<String> actual = Selection.classHierarchyAnalysis(project_target,change_info,arg);
            Boolean argExist = true;
            switch (arg){
                case "c":
                    expect = Selection.readFileByLines(dataPath+"/selection-class.txt");
                    break;
                case "m":
                    expect = Selection.readFileByLines(dataPath+"/selection-method.txt");
                    break;
                default:
                    break;
            }
            if(argExist){
                Assert.assertEquals(expect.size(),actual.size());
                Collections.sort(expect);
                Collections.sort(actual);
                Boolean select = true;
                for(int i=0;i<expect.size();i++){
                    if(!expect.get(i).equals(actual.get(i))){
                        select = false;
                        break;
                    }
                }
                Assert.assertTrue(select);
            }else {
                Assert.assertNull(actual);
            }
        }catch (Exception e){
            e.printStackTrace();
            Assert.fail();
        }

    }
}
