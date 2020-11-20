import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.classLoader.ShrikeBTMethod;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.cha.CHACallGraph;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.config.AnalysisScopeReader;

import java.io.*;
import java.util.*;

public class Selection {

    // 获取一个目录下的所有类文件的经过解析的绝对路径
    public static List<String> listClassFiles(File rootFile, List<String> fileList) throws IOException{
        File[] allFiles = rootFile.listFiles();
        if(allFiles!=null){
            for(File file : allFiles){
                if(file.isDirectory()){
                    listClassFiles(file, fileList);
                }else{
                    String path = file.getCanonicalPath();
//                System.out.println(path);
                    if(path.endsWith(".class")){
//                    System.out.println("here");
                        fileList.add(path);
                    }
//                String clazz = path.substring(path.indexOf("classes")+8);
//                fileList.add(clazz.replace("//", ".").substring(0,clazz.lastIndexOf(".")));
                }
            }
        }
        return fileList;
    }

    // 将project_target下的类加入到scope中
    public static void addToScope(String project_target,AnalysisScope scope) throws IOException, InvalidClassFileException {
        File rootFile = new File(project_target);
//        System.out.println(rootFile.getCanonicalPath());
        for(String file : listClassFiles(rootFile, new ArrayList<String>())){
            scope.addClassFileToScope(ClassLoaderReference.Application, new File(file));
        }
    }

    // 按行读取文件存到数组中
    public static ArrayList<String> readFileByLines(String fileName) {
        ArrayList<String> lines = new ArrayList<String>();
        File file = new File(fileName);
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String tempString = null;
            while ((tempString = reader.readLine()) != null) {
                if(!tempString.matches("\\s*")){//确保加入非空的串
                    lines.add(tempString.trim());
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
            lines = null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e1) {
                }
            }
        }
        return lines;
    }

    //将数组文件的每一项作为一行写到文件中
    public static void writeFileByLInes(String path,ArrayList<String> context){
        File file = new File(path);
        try {
            if(!file.exists()) {
                file.createNewFile();
            } else {
                file.delete();
                file.createNewFile();
            }

            FileWriter fw = new FileWriter(file, true);
            BufferedWriter bw = new BufferedWriter(fw);

            for(String line:context) {
                line += "\r\n";
                bw.write(line);
            }
            bw.flush();
            bw.close();
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 获取一个CGNode的签名
    public static String getName(CGNode node){
        if(node.getMethod() instanceof ShrikeBTMethod){
            // node.getMethod()返回一个比较泛化的IMethod实例，不能获取到我们想要的信息
            // 一般地，本项目中所有和业务逻辑相关的方法都是ShrikeBTMethod对象
            ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
            // 使用Primordial类加载器加载的类都属于Java原生类，我们一般不关心。
            if("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                // 获取声明该方法的类的内部表示
                String classInnerName = method.getDeclaringClass().getName().toString();
                // 获取方法签名
                String signature = method.getSignature();
                return classInnerName + " " + signature;
            }
//            System.out.println(String.format("'%s' is ShrikeBTMethod but not fit Application",node.getMethod()));
        }else {
//            System.out.println(String.format("'%s'不是一个ShrikeBTMethod：%s", node.getMethod(), node.getMethod().getClass()));
        }
        return null;
    }

    // 通过project_target目录位置以及tag表明的类粒度、方法粒度的选择，来生成dot文件（dot文件用来paint）
    public static boolean generateDot(String project_target,String tag) throws IOException, InvalidClassFileException, ClassHierarchyException, CancelException {
        //通过project_target建立CHAGraph，知道了节点之间的关系
        CHACallGraph cg = buildCHAGraph(project_target);

        ArrayList<CGNode> nodes = new ArrayList<>();
        // 遍历cg中所有的节点
        for(CGNode node: cg) {
            String tmpName = getName(node);
            if(tmpName!=null) {//若tmpName不为null,说明是application，要加入nodes中
                nodes.add(node);
            }
        }

        // 构建类粒度的依赖关系dot文件
        try{
            LinkedHashMap<String,LinkedList<String>> graph = makeGraph(cg,nodes,0);
            hashMapToDot(graph,"digraph cmd_class {","./src/main/resources/class-"+tag+".dot");
        }catch (Exception e){
            e.printStackTrace();
            return false;
        }

        // 构建方法粒度的依赖关系dot文件
        try{
            LinkedHashMap<String,LinkedList<String>> graph = makeGraph(cg,nodes,1);
            hashMapToDot(graph,"digraph cmd_method {","./src/main/resources/method-"+tag+".dot");
        }catch (Exception e){
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static void hashMapToDot(LinkedHashMap<String,LinkedList<String>> graph,String head, String path){
        //将表示依赖关系的哈希表graph转化为一行为一项的依赖关系数组，方便输入到dot文件中
        ArrayList<String> dot = new ArrayList<>();
        dot.add(head);
        for(String key:graph.keySet()){
            LinkedList<String> pres = graph.get(key);
            for (String pre : pres) {
                dot.add("    \"" + key + "\" -> \"" + pre + "\";");
            }
        }
        dot.add("}");
        writeFileByLInes(path,dot);
    }

    // 通过CHACallGraph和CGNode的集合知道节点之间的关系，然后生成类粒度或者方法粒度的哈希表返回
    // no决定了key为签名的前半部分（类签名）还是后半部分（方法签名）
    public static LinkedHashMap<String,LinkedList<String>> makeGraph(CHACallGraph cg,ArrayList<CGNode> nodes,int no){
        if(no<0||no>1)return null;
        LinkedHashMap<String,LinkedList<String>> relation = new LinkedHashMap<>();
        for(CGNode node: nodes){
            String name = getName(node).split(" ")[no];
            if(!relation.containsKey(name)){// 确保每个节点一定都在哈希表的keyset中存在
                relation.put(name,new LinkedList<String>());
            }
            LinkedList<String> preRecord = relation.get(name);
            Iterator<CGNode> pres = cg.getPredNodes(node);
            while(pres.hasNext()){
                String tmpName = getName(pres.next());
                if(tmpName!=null){//!=null即为application，如果节点node有作为application的前继，那么尝试不重复地加入到前继的记录（即哈希表）中
                    String preName = tmpName.split(" ")[no];
                    if(!preRecord.contains(preName)){
                        preRecord.add(preName);
                    }
                }
            }
        }
        return relation;
    }

    public static CHACallGraph buildCHAGraph(String project_target) throws IOException, InvalidClassFileException, ClassHierarchyException, CancelException {
        // 构建分析域（AnalysisScope）对象scope
        AnalysisScope scope= AnalysisScopeReader.readJavaScope("scope.txt", new File("exclusion.txt"),  Selection.class.getClassLoader());
        // 将project_target目录（不限于Maven格式）下所有类对象加入scope
        addToScope(project_target,scope);
//        System.out.println(scope);

        // 生成类层次关系对象
        ClassHierarchy cha = ClassHierarchyFactory.makeWithRoot(scope);
//        for (IClass iClass : cha) {
//            if(iClass.toString().contains("Application"))
//                System.out.println(iClass);
//        }

        // 生成进入点
        Iterable<Entrypoint> eps = new AllApplicationEntrypoints(scope, cha);
        // 利用CHA算法构建调用图
        CHACallGraph cg = new CHACallGraph(cha);

        cg.init(eps);
//        // Test CallGraphStats
//        String stats = CallGraphStats.getStats(cg);
//        System.out.println(stats);

        return cg;
    }

    //通过读取project_target和change_info以及选择参数（类粒度还是方法粒度）来将需要重新运行的测试用例输出到./目录下的文件中，返回值的存在是为了测试
    public static ArrayList<String> classHierarchyAnalysis(String project_target,String change_info,String arg) throws ClassHierarchyException, CancelException, IOException, InvalidClassFileException {
        //通过project_target建立CHAGraph，知道了节点之间的关系
        CHACallGraph cg = buildCHAGraph(project_target);
        // 获取所有的已改变方法
        ArrayList<String> change_methods = readFileByLines(change_info);
        // 建立筛选的几个分区
        Queue<CGNode> infected_nodes = new LinkedList<>();
        ArrayList<CGNode> nodes = new ArrayList<>();
        // 遍历cg中所有的节点
        for(CGNode node: cg) {
            String tmpName = getName(node);
            if(tmpName!=null) {//!=null即为application方法
                if(change_methods.contains(tmpName)){
                    infected_nodes.add(node);//infected_nodes中为受影响的节点
                }else {
                    nodes.add(node);//nodes为筛去“目前发现的”受影响节点后的节点
                }
            }
        }

        //读取/test-classes目录下的所有类的名字转化为类签名，供之后识别一个类是不是测试类
        ArrayList<String> testPathNames = new ArrayList<>();
        ArrayList<String> testClassNames = new ArrayList<>();
        listClassFiles(new File(project_target+"/test-classes"),testPathNames);
        for(int i=0;i<testPathNames.size();i++){
            String name = testPathNames.get(i);
            String entrance = "test-classes";
            name = "L"+name.substring(name.indexOf(entrance)+entrance.length()+1);
            name = name.substring(0,name.length()-".class".length());
            name = name.replaceAll("\\\\","/");
            testClassNames.add(name);
        }

        //根据参数进行不同用例选择的行为
        ArrayList<String> selected_tests = null;
        switch (arg){
            case "c":
                selected_tests = class_selection(cg,infected_nodes,nodes,testClassNames);
                writeFileByLInes("./selection-class.txt",selected_tests);
                break;
            case "m":
                selected_tests = method_selection(cg,infected_nodes,nodes,testClassNames);
                writeFileByLInes("./selection-method.txt",selected_tests);
                break;
            default:
                System.out.println("arg wrong");
                break;
        }
        return selected_tests;//有返回值是为了测试
    }

    // 通过已经收集好的测试类的签名，判断一个类是不是测试类
    public static boolean isTestClass(String name,ArrayList<String> testClassNames){
        return testClassNames.contains(name);
    }

    // 从可修改性的角度考虑封装node相等判断的逻辑
    public static boolean nodeEqual(CGNode node1, CGNode node2){
        return node1.equals(node2);
    }

    // 实现nodes的contains方法（实际上可以直接用List<>原生方法）
    public static boolean nodeContain(List<CGNode> nodes,CGNode toFind){
        for(CGNode node:nodes){
            if(nodeEqual(node,toFind)){
                return true;
            }
        }
        return false;
    }

    // 类粒度选择的受影响测试方法的签名集合
    public static ArrayList<String> class_selection(CHACallGraph cg,Queue<CGNode> infected_nodes,ArrayList<CGNode> nodes,ArrayList<String> testClassNames){
        //获取潜在的被选择节点的签名
        ArrayList<String> nodeNames = new ArrayList<>();
        for(CGNode node: nodes){
            nodeNames.add(getName(node));
        }

        nodes.addAll(infected_nodes);//加上infected_nodes后nodes表示全部application节点
        LinkedHashMap<String,LinkedList<String>> classRelation = makeGraph(cg,nodes,0);//构建类粒度的哈希表

        // 构建被影响的类的类名集合
        Queue<String> infected_classes = new LinkedList<>();
        for(CGNode node: infected_nodes){
            String className = getName(node).split(" ")[0];
            if(!infected_classes.contains(className)){
                infected_classes.add(className);
            }
        }

        // 构建所有的application类的类名集合
        ArrayList<String> classNames = new ArrayList<>();
        for(String name:classRelation.keySet()){
            if(!infected_classes.contains(name)){
                classNames.add(name);
            }
        }

        // 获取要选择的测试类的类名
        ArrayList<String> selected_test_class = new ArrayList<>();
        String tmpClass = null; //以下为类名集合不断被被影响的类名污染的过程
        while(!classNames.isEmpty()&&(tmpClass=infected_classes.poll())!=null){//没有受害者或没有污染源时停止
            LinkedList<String> pres = classRelation.get(tmpClass);
            for(int i=0;i<pres.size();i++){//队列中的每一个污染源都可以由被它污染的且在nodes中的节点代替，这个迭代保证nodes节点渐少
                String victim = pres.get(i);
                if(classNames.contains(victim)){//当污染源成功影响到集合中的节点时
                    if(!classNames.remove(victim)){//移除该节点
                        System.out.println("remove name from classNames fail");
                    }
                    if(isTestClass(victim,testClassNames)){//若该被影响的节点为测试类，则记录
                        selected_test_class.add(victim);
                    }
                    if(!classRelation.get(victim).isEmpty()){//被影响到的节点可以作为新的污染源加入队列
                        infected_classes.add(victim);
                    }
                }
            }
        }

        // 通过要选择的测试类的类名获取要选择的测试方法
        ArrayList<String> selected_tests = new ArrayList<>();
        for(String tmpName: nodeNames){
            if(selected_test_class.contains(tmpName.split(" ")[0])){
                if(!tmpName.contains("init")){//不考虑初始化方法
                    selected_tests.add(tmpName);
                }
            }
        }
        return selected_tests;
    }

    // 方法粒度选择的受影响测试方法的签名集合
    public static ArrayList<String> method_selection(CHACallGraph cg,Queue<CGNode> infected_nodes,ArrayList<CGNode> nodes,ArrayList<String> testClassNames){
        ArrayList<String> selected_tests = new ArrayList<>();
        // 获取被影响到的测试方法
        CGNode tmpNode = null; //以下为类名集合不断被被影响的类名污染的过程
        while(!nodes.isEmpty()&&(tmpNode=infected_nodes.poll())!=null){//没有受害者或没有污染源时停止
            Iterator<CGNode> pres = cg.getPredNodes(tmpNode);
            while(pres.hasNext()){//队列中的每一个污染源都可以由被它污染的且在nodes中的节点代替，这个迭代保证nodes节点渐少
                CGNode victim = pres.next();
                if(nodeContain(nodes,victim)){//当污染源成功影响到集合中的节点时
                    if(!nodes.remove(victim)){//移除该节点
                        System.out.println("remove node from nodes fail");
                    }
                    String tmpName = getName(victim);
                    if(isTestClass(tmpName.split(" ")[0],testClassNames)){//若该被影响的节点为测试类，则记录
                        //tmpName不可能为null，因为victim属于nodes，而nodes在classHierarchyAnalysis里筛选过
                        if(!tmpName.contains("init")){//不考虑初始化方法
                            selected_tests.add(tmpName);
                        }
                    }
                    if(cg.getPredNodeCount(victim)!=0){//被影响到的节点可以作为新的污染源加入队列
                        infected_nodes.add(victim);
                    }
                }
            }
        }
        return selected_tests;
    }

    public static void main(String[] args) {

        try {
            String mode = args[0].substring(1);
            String project_target = args[1];
            String change_info = args[2];
            classHierarchyAnalysis(project_target,change_info,mode);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}