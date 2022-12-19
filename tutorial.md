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

