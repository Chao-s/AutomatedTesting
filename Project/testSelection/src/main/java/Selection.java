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

    /**
     * 获取所有文件列表
     * @param rootFile
     * @param fileList
     * @throws IOException
     */
    public static List<String> listClassFiles(File rootFile, List<String> fileList) throws IOException{
        File[] allFiles = rootFile.listFiles();//有时间改一下判断方法
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

    public static void addToScope(String project_target,AnalysisScope scope) throws IOException, InvalidClassFileException {
        File rootFile = new File(project_target);
//        System.out.println(rootFile.getCanonicalPath());
        for(String file : listClassFiles(rootFile, new ArrayList<String>())){
            scope.addClassFileToScope(ClassLoaderReference.Application, new File(file));
        }
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

    public static ArrayList<String> readFileByLines(String fileName) {
        ArrayList<String> lines = new ArrayList<String>();
        File file = new File(fileName);
        BufferedReader reader = null;
        try {
//            System.out.println("以行为单位读取文件内容，一次读一整行：");
            reader = new BufferedReader(new FileReader(file));
            String tempString = null;
            // 一次读入一行，直到读入null为文件结束
            while ((tempString = reader.readLine()) != null) {
                // 显示行号
                if(!tempString.matches("\\s*")){
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

//        System.out.println("Write is over");
    }

    public static String getName(CGNode node){
        if(node.getMethod() instanceof ShrikeBTMethod){
            // node.getMethod()返回一个比较泛化的IMethod实例，不能获取到我们想要的信息
            // 一般地，本项目中所有和业务逻辑相关的方法都是ShrikeBTMethod对象
            ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
            // 使用Primordial类加载器加载的类都属于Java原生类，我们一般不关心。
            if("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                // 获取声明该方法的类的内部表示
                String classInnerName =
                        method.getDeclaringClass().getName().toString();
                // 获取方法签名
                String signature = method.getSignature();
                return classInnerName + " " + signature;
            }
//            System.out.println(String.format("'%s' is ShrikeBTMethod but not fit Application",node.getMethod()));
        }else {
//            System.out.println(String.format("'%s'不是一个ShrikeBTMethod：%s",
//                    node.getMethod(),
//                    node.getMethod().getClass()));
        }
        return null;
    }

    public static boolean nodeEqual(CGNode node1, CGNode node2){
        return node1.equals(node2);
    }

    public static boolean nodeContain(List<CGNode> nodes,CGNode toFind){
        for(CGNode node:nodes){
            if(nodeEqual(node,toFind)){
                return true;
            }
        }
        return false;
    }

    public static boolean dotToPaint(String project_target,String tag) throws IOException, InvalidClassFileException, ClassHierarchyException, CancelException {
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

        ArrayList<CGNode> nodes = new ArrayList<CGNode>();
        // 遍历cg中所有的节点
//        System.out.println("所有application节点如下：");
        for(CGNode node: cg) {
            // node中包含了很多信息，包括类加载器、方法信息等，这里只筛选出需要的信息
            String tmpName = getName(node);
            if(tmpName!=null) {
//                System.out.println(tmpName);
                nodes.add(node);
            }
        }
//        System.out.println();

        try{
            LinkedHashMap<String,LinkedList<String>> graph = makeGraph(cg,nodes,"c");
            ArrayList<String> dot = new ArrayList<>();
            dot.add("digraph cmd_class {");
            for(String key:graph.keySet()){
                LinkedList<String> pres = graph.get(key);
                for(int i=0;i<pres.size();i++){
                    dot.add("    \""+key+"\" -> \""+pres.get(i)+"\";");
                }
            }
            dot.add("}");
            writeFileByLInes("./src/main/resources/class-"+tag+".dot",dot);
        }catch (Exception e){
            e.printStackTrace();
            return false;
        }

        try{
            LinkedHashMap<String,LinkedList<String>> graph = makeGraph(cg,nodes,"m");
            ArrayList<String> dot = new ArrayList<>();
            dot.add("digraph cmd_method {");
            for(String key:graph.keySet()){
                LinkedList<String> pres = graph.get(key);
                for(int i=0;i<pres.size();i++){
                    dot.add("    \""+key+"\" -> \""+pres.get(i)+"\";");
                }
            }
            dot.add("}");
            writeFileByLInes("./src/main/resources/method-"+tag+".dot",dot);
        }catch (Exception e){
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static ArrayList<String> classHierarchyAnalysis(String project_target,String change_info,String arg) throws ClassHierarchyException, CancelException, IOException, InvalidClassFileException {
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

        // 获取所有的已改变方法
        ArrayList<String> change_methods = readFileByLines(change_info);
        // 建立筛选的几个分区
        Queue<CGNode> infected_nodes = new LinkedList<CGNode>();
        ArrayList<CGNode> nodes = new ArrayList<CGNode>();
        // 遍历cg中所有的节点
//        System.out.println("所有application节点如下：");
        for(CGNode node: cg) {
            // node中包含了很多信息，包括类加载器、方法信息等，这里只筛选出需要的信息
            String tmpName = getName(node);
            if(tmpName!=null) {
//                System.out.println(tmpName);
                if(change_methods.contains(tmpName)){
                    infected_nodes.add(node);
                }else {
                    nodes.add(node);
                }
            }
        }

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
        switch (arg){
            case "c":
                return class_selection(cg,infected_nodes,nodes,testClassNames);
            case "m":
                return method_selection(cg,infected_nodes,nodes,testClassNames);
            default:
                System.out.println("arg wrong");
                return null;
        }

    }

    public static boolean isTestClass(String name,ArrayList<String> testClassNames){
        return testClassNames.contains(name);
    }

    public static LinkedHashMap<String,LinkedList<String>> makeGraph(CHACallGraph cg,ArrayList<CGNode> nodes,String arg){
        int no = -1;
        switch (arg){
            case "c":
                no = 0;
                break;
            case "m":
                no = 1;
                break;
            default:
                return null;
        }

        LinkedHashMap<String,LinkedList<String>> relation = new LinkedHashMap<>();
        for(CGNode node: nodes){
            String name = getName(node).split(" ")[no];
            if(!relation.containsKey(name)){
                relation.put(name,new LinkedList<String>());
            }
            LinkedList<String> preRecord = relation.get(name);
            Iterator<CGNode> pres = cg.getPredNodes(node);
            while(pres.hasNext()){
                String tmpName = getName(pres.next());
                if(tmpName!=null){
                    String preName = tmpName.split(" ")[no];
                    if(!preRecord.contains(preName)){
                        preRecord.add(preName);
                    }
                }
            }
        }
        return relation;
    }

    public static ArrayList<String> class_selection(CHACallGraph cg,Queue<CGNode> infected_nodes,ArrayList<CGNode> nodes,ArrayList<String> testClassNames){
        ArrayList<String> nodeNames = new ArrayList<>();
        for(CGNode node: nodes){
            nodeNames.add(getName(node));
        }

        nodes.addAll(infected_nodes);
        LinkedHashMap<String,LinkedList<String>> classRelation = makeGraph(cg,nodes,"c");

        Queue<String> infected_classes = new LinkedList<String>();
        for(CGNode node: infected_nodes){
            String className = getName(node).split(" ")[0];
            if(!infected_classes.contains(className)){
                infected_classes.add(className);
            }
        }

        ArrayList<String> classNames = new ArrayList<>();
        for(String name:classRelation.keySet()){
            if(!infected_classes.contains(name)){
                classNames.add(name);
            }
        }

        ArrayList<String> infected_test_class = new ArrayList<>();
        ArrayList<String> infected_tests = new ArrayList<>();

        String tmpClass = null;
        while(!classNames.isEmpty()&&(tmpClass=infected_classes.poll())!=null){
            LinkedList<String> pres = classRelation.get(tmpClass);
            for(int i=0;i<pres.size();i++){
                String victim = pres.get(i);
                if(classNames.contains(victim)){
                    if(!classNames.remove(victim)){
                        System.out.println("remove name from classNames fail");
                    }
                    if(isTestClass(victim,testClassNames)){
                        infected_test_class.add(victim);
                    }
                    if(!classRelation.get(victim).isEmpty()){
                        infected_classes.add(victim);
                    }
                }
            }
        }

        for(String tmpName: nodeNames){
            if(infected_test_class.contains(tmpName.split(" ")[0])){
                if(!tmpName.contains("init")){
                    infected_tests.add(tmpName);
                }
            }
        }

        writeFileByLInes("./selection-class.txt",infected_tests);
        return infected_tests;
    }

    public static ArrayList<String> method_selection(CHACallGraph cg,Queue<CGNode> infected_nodes,ArrayList<CGNode> nodes,ArrayList<String> testClassNames){
        ArrayList<String> infected_tests = new ArrayList<>();

        CGNode tmpNode = null;
        while(!nodes.isEmpty()&&(tmpNode=infected_nodes.poll())!=null){
            Iterator<CGNode> pres = cg.getPredNodes(tmpNode);
            while(pres.hasNext()){
                CGNode victim = pres.next();
                if(!nodeEqual(victim,tmpNode)&&nodeContain(nodes,victim)){//nodeEqual(victim,tmpNode)不必要
                    if(!nodes.remove(victim)){
                        System.out.println("remove node from nodes fail");
                    }
                    String tmpName = getName(victim);
                    if(isTestClass(tmpName.split(" ")[0],testClassNames)){//tmpName不可能为null，因为在classHierarchyAnalysis里筛选过
                        if(!tmpName.contains("init")){
                            infected_tests.add(tmpName);
                        }
                    }
                    if(cg.getPredNodeCount(victim)!=0){
                        infected_nodes.add(victim);
                    }
                }
            }
        }

        writeFileByLInes("./selection-method.txt",infected_tests);
        return infected_tests;
    }

    public static void zero_cfa(AnalysisScope scope) throws ClassHierarchyException {

        ClassHierarchy cha = ClassHierarchyFactory.makeWithRoot(scope);
        AllApplicationEntrypoints entrypoints = new AllApplicationEntrypoints(scope,
                cha);
        AnalysisOptions option = new AnalysisOptions(scope, entrypoints);
        SSAPropagationCallGraphBuilder builder = Util.makeZeroCFABuilder(
                Language.JAVA, option, new AnalysisCacheImpl(), cha, scope
        );
        //下略
    }
}

