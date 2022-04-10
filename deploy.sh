cd api
mvn clean install
cd ..
if [ -d docker/target ]; then
   rm -rf docker/target
fi
mv api/target docker/
cdk deploy --require-approval never