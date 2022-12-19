# Simple onboarding and payment webpage

Goal : create a webpage with a form that allow a user to onboard and pay for a service. In the backend the form is saved in DB and and a distant rest API (here Stripe) is called. The result of the AI query is displayed in the page

Prereq: Meveo is deployed on a server or locally

## Create the backend

In the form the user will input his name and email then select if he accept to receive commercial emails.

In `Configuration/Module` press the button `New` to create a module `mv-onboarging`

![image](https://user-images.githubusercontent.com/16659140/208433446-6a77eee1-30d9-4c4f-a078-2a973e3c8138.png)

then click the `Save` button.

On the top right `Current Module` dropbox select the `mv-onboarging` module

![image](https://user-images.githubusercontent.com/16659140/208433646-299bf54e-393f-4a3c-a12f-b663aa2e12df.png)

In `Ontology/Custom Entity Categories` create a new category with code `onboarding`

![image](https://user-images.githubusercontent.com/16659140/208433946-7e4a7cfd-d5cb-4419-8572-c1c14a4052cc.png)

Since we selected  `mv-onboarging` as the current module all item we create are automatically added to that module.

In `Ontology/Entity Customization` create a new entity with code `OnboardingForm` in the `onboarding` category, store it only in SQL.

![image](https://user-images.githubusercontent.com/16659140/208434630-ec5dddc5-d242-4a11-b9c7-bf874711da3c.png)

click on the `Save button`.

add to it custom fields
by clicking the `Add` button in the actions icons of the CFT list in the CET
![image](https://user-images.githubusercontent.com/16659140/208434860-d00b872f-a240-47f0-8354-e727b38472b2.png)

a CFT `name` of type String, 
![image](https://user-images.githubusercontent.com/16659140/208434979-0ba06fc8-b865-4116-8121-cdf7bf205464.png)

a CFT `email`  of type String 
and a CFT `commercialEmailAccepted` of type boolean

![image](https://user-images.githubusercontent.com/16659140/208435275-8f788f63-b694-4b1f-b472-bbbc946f0a3a.png)

## Install module-webapprouter

To install the module we will ask meveo to clone its git repository and install.
First we need to get the https url of the git repository of the [module](https://github.com/meveo-org/module-webapprouter/)

![image](https://user-images.githubusercontent.com/16659140/208435762-98af2a58-4e20-45de-ba22-7ae68401c339.png)

In meveo open the menu `Configuration/Storages/Git repositories`

![image](https://user-images.githubusercontent.com/16659140/208435993-4b7965ce-1425-470a-9f61-da9269822be9.png)

Create a repository `module-webapprouter`

![image](https://user-images.githubusercontent.com/16659140/208436443-7d6d1267-3b97-47d8-847a-0ceb69166876.png)

and click the `Save` button, you can delete the content of username ans password in the input fields as this repository is public

then edit the newly created repo
![image](https://user-images.githubusercontent.com/16659140/208436950-9812bff8-920a-46be-9426-c09cf03ecb86.png)

and click the `Install Module` button

![image](https://user-images.githubusercontent.com/16659140/208437118-0aed48e1-416a-41a4-b10b-4ab31ec589a8.png)

Select the `default` repository and clik Install
![image](https://user-images.githubusercontent.com/16659140/208437241-4cf8ee94-b1eb-4a8c-834f-5470b12e7e6b.png)

If the installation succeeds, you should see a message 
![image](https://user-images.githubusercontent.com/16659140/208437359-49dee509-7ec5-420a-a494-61e365f7eae1.png)

and by clicking on the menu  `Configuration / Modules` you should see `module-webaprouter` installed
![image](https://user-images.githubusercontent.com/16659140/208437581-0673c361-5713-4c14-a93a-ace54c89777e.png)


## Generate the webapp of our onboarding module

The `webaprouter` module install an `Apps` menu in meveo, click on `Apps/Web Application` and click `New` button.

Create a web application with code `mv-onboarding`, the code of the module we want to export as a webapp.

![image](https://user-images.githubusercontent.com/16659140/208462846-6bd1cd40-d562-4f30-bf05-9ec1a32ef0ee.png)

then click `Save` button.

Edit it and click the `Generate Web Application` button. It will take a while but if successfull you will see a message 


and in the log you should see 
```
15:37:15,303 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) CREATE NEW GitRepository: WebApplication
15:37:17,793 DEBUG [org.meveo.service.git.GitRepositoryService] (default task-3) Start creation of entity GitRepository
15:37:17,794 DEBUG [org.meveo.service.notification.DefaultObserver] (default task-3) Defaut observer : Entity org.meveo.model.git.GitRepository with id null created
15:37:17,803 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) webappTemplate path: ./meveodata/default/git/WebApplication
15:37:17,825 DEBUG [org.meveo.service.git.GitRepositoryService] (default task-3) Start creation of entity GitRepository
15:37:17,825 DEBUG [org.meveo.service.notification.DefaultObserver] (default task-3) Defaut observer : Entity org.meveo.model.git.GitRepository with id null created
15:37:17,852 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) moduleWebApp branch: meveo
15:37:17,852 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) moduleWebApp path: ./meveodata/default/git/mv-onboarding-UI
15:37:17,867 DEBUG [org.meveo.script.FileTransformer] (default task-3) output file name: ./meveodata/default/git/mv-onboarding-UI/components/layout/TopbarMenu.js
15:37:17,873 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) keycloakUrl: http://host.docker.internal:8081/auth
15:37:17,874 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) sourceFile: ./meveodata/default/git/WebApplication/keycloak.json
15:37:17,874 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) destinationFile: ./meveodata/default/git/mv-onboarding-UI/keycloak.json
15:37:17,874 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) substitutions: {http://host.docker.internal:8081/auth=http://host.docker.internal:8081/auth, meveo=meveo, meveo-web=meveo-web}
15:37:17,875 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) GENERATE NEW PAGE
15:37:17,875 DEBUG [org.meveo.script.FileTransformer] (default task-3) output file name: ./meveodata/default/git/mv-onboarding-UI/pages/OnboardingForm/NewPage.js
15:37:17,876 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) GENERATE LIST PAGE
15:37:17,876 DEBUG [org.meveo.script.FileTransformer] (default task-3) output file name: ./meveodata/default/git/mv-onboarding-UI/pages/OnboardingForm/ListPage.js
15:37:17,877 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) GENERATE UPDATE PAGE
15:37:17,877 DEBUG [org.meveo.script.FileTransformer] (default task-3) output file name: ./meveodata/default/git/mv-onboarding-UI/pages/OnboardingForm/UpdatePage.js
15:37:17,878 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) GENERATE MODELS
15:37:17,878 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) source path: ./meveodata/default/git/WebApplication/model/Parent.js
15:37:17,878 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) GENERATE MODEL FILES
15:37:17,878 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) output file name: ./meveodata/default/git/mv-onboarding-UI/model/OnboardingForm.js
15:37:17,901 DEBUG [org.meveo.script.EntityActions] (default task-3) actions: []
15:37:17,903 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) GENERATE MODELS
15:37:17,903 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) source path: ./meveodata/default/git/WebApplication/model/index.js
15:37:17,903 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) GENERATE MODELS
15:37:17,903 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) source path: ./meveodata/default/git/WebApplication/model/Child.js
15:37:17,903 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) GENERATE MODELS
15:37:17,903 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) source path: ./meveodata/default/git/WebApplication/model/ChildSchema.js
15:37:17,903 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) GENERATE MODELS
15:37:17,904 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) source path: ./meveodata/default/git/WebApplication/model/ParentSchema.js
15:37:17,905 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) sourceFile: ./meveodata/default/git/WebApplication/config.js
15:37:17,905 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) destinationFile: ./meveodata/default/git/mv-onboarding-UI/config.js
15:37:17,905 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) substitutions: {MODULE_CODE=mv-onboarding, http://localhost:8080/=http://localhost:8080/, WEB_CONTEXT=/meveo}
15:37:17,980 DEBUG [org.meveo.service.admin.impl.MeveoModuleService] (default task-3) No MeveoModule of code mv-onboarding-UI found
15:37:17,981 DEBUG [org.meveo.script.GenerateWebAppScript] (default task-3) switch to moduleWebApp branch: master
15:37:18,052 INFO  [org.meveo.script.GenerateWebAppScript] (default task-3) ***********************************************************
15:37:18,052 INFO  [org.meveo.script.GenerateWebAppScript] (default task-3) *************  SUCCESSFULLY MERGED TO MASTER  *************
15:37:18,052 INFO  [org.meveo.script.GenerateWebAppScript] (default task-3) ***********************************************************
```

