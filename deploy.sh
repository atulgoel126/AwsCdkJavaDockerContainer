cd spring-petclinic
mvn clean install
cd ..
if [ -d docker/target ]; then
   rm -rf docker/target
fi
mv spring-petclinic/ target docker/
cdk deploy --require-approval never