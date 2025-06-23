cd /root/ComplaintService/Support
mvn clean
rm -rf target/*
mvn package
ls -l target/ROOT.war


/opt/tomcat2/bin/shutdown.sh
sudo rm -rf /opt/tomcat2/webapps/Support*
sudo rm -rf /opt/tomcat2/work/Catalina/localhost/Support/
sudo rm -rf /opt/tomcat2/logs/*


cp /root/ComplaintService/Support/target/ROOT.war /opt/tomcat2/webapps/
/opt/tomcat2/bin/startup.sh
