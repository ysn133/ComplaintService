cd /home/yassin/ComplaintService/ComplaintService/jsflogin
mvn clean
rm -rf target/*
mvn package
ls -l target/jsflogin.war


/opt/tomcat/bin/shutdown.sh
sudo rm -rf /opt/tomcat/webapps/jsflogin*
sudo rm -rf /opt/tomcat/work/Catalina/localhost/jsflogin/
sudo rm -rf /opt/tomcat/logs/*


cp /home/yassin/ComplaintService/ComplaintService/jsflogin/target/jsflogin.war /opt/tomcat/webapps/
/opt/tomcat/bin/startup.sh
