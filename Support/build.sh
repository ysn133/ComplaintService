cd /home/yassin/ComplaintService/ComplaintService/support
mvn clean
rm -rf target/*
mvn package
ls -l target/support.war


/opt/tomcat2/bin/shutdown.sh
sudo rm -rf /opt/tomcat2/webapps/Support*
sudo rm -rf /opt/tomcat2/work/Catalina/localhost/Support/
sudo rm -rf /opt/tomcat2/logs/*


cp /home/yassin/ComplaintService/ComplaintService/Support/target/support.war /opt/tomcat2/webapps/
/opt/tomcat2/bin/startup.sh
