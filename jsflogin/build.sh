cd /root/ComplaintService/jsflogin

rm -rf target/*
mvn clean
mvn package
ls -l target/ROOT.war


/opt/tomcat/bin/shutdown.sh
sudo rm -rf /opt/tomcat/webapps/jsflogin*
sudo rm -rf /opt/tomcat/work/Catalina/localhost/jsflogin/
sudo rm -rf /opt/tomcat/logs/*


cp /root/ComplaintService/jsflogin/target/ROOT.war /opt/tomcat/webapps/
/opt/tomcat/bin/startup.sh
