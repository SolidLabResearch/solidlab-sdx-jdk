# Running the demo app

## Populate a Solid Pod
We'll be using the [Solid Community server](https://communitysolidserver.github.io/CommunitySolidServer/) to host both the Shape file and the demo data.

To set up a file-backed CSS instance (using the current folder as storage), execute:

```
npm install -g @solid/community-server
community-solid-server -c @css:config/file.json
```

Add the example files:

1. SHACL definition
```
curl --request PUT \
  --url http://localhost:3000/shapes/contact-SHACL.ttl \
  --header 'Content-Type: text/turtle' \
  --data '@prefix sh: <http://www.w3.org/ns/shacl#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix schema: <http://schema.org/> .
@prefix ex: <http://example.org/> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

ex:Contact
    a rdfs:Class, sh:NodeShape ;
    sh:targetClass schema:Person;
    sh:property
            [
          sh:path schema:givenName ;
          sh:datatype xsd:string ;
          sh:minCount 1 ;
          sh:maxCount 1 ;
      ],
            [
          sh:path schema:familyName ;
          sh:datatype xsd:string ;
          sh:minCount 1 ;
          sh:maxCount 1;
      ],
            [
          sh:path schema:email;
          sh:datatype xsd:string ;
      ],
            [
          sh:path schema:address ;
          sh:class schema:PostalAddress ;
          sh:maxCount 1;
      ],
            [
          sh:path schema:worksFor ;
          sh:class schema:Organization ;
      ].

ex:Address
    a rdfs:Class , sh:NodeShape ;
    sh:targetClass schema:PostalAddress ;
    sh:property [
          sh:name "country" ;
          sh:path schema:addressCountry ;
          sh:datatype xsd:string ;
          sh:minCount 1;
          sh:maxCount 1;
      ],
    [
          sh:name "city" ;
          sh:path schema:addressLocality ;
          sh:datatype xsd:string ;
          sh:minCount 1;
          sh:maxCount 1;
      ],
    [
          sh:name "streetLine" ;
          sh:path schema:streetAddress ;
          sh:datatype xsd:string ;
          sh:minCount 1;
          sh:maxCount 1;
      ],
    [
          sh:name "postalCode" ;
          sh:path schema:postalCode ;
          sh:datatype xsd:string ;
          sh:minCount 1;
          sh:maxCount 1;
      ].

ex:Organization
    a rdfs:Class , sh:NodeShape ;
    sh:targetClass schema:Organization  ;
    sh:property [
          sh:path schema:name  ;
          sh:datatype xsd:string ;
          sh:minCount 1;
          sh:maxCount 1;
      ],
                [
                    sh:path schema:address ;
                    sh:class schema:PostalAddress ;
                    sh:maxCount 1;
                ].
'
```

2. Demo data file
```
curl --request PUT \
  --url http://localhost:3000/contacts/jdoe.ttl \
  --header 'Content-Type: text/turtle' \
  --data '@prefix schema: <http://schema.org/> .

<https://example.com/persons/jdoe> a schema:Person ;
	schema:givenName "John" ;
	schema:familyName "Doe";
	schema:email "john.doe@gmail.com";
	schema:email "john.doe@somecorp.org";
	schema:address [
      a schema:PostalAddress ;
      schema:streetAddress "Some Road 99";
      schema:addressLocality "Random City";
      schema:postalCode "99999";
      schema:addressCountry "Country";
    ];
	schema:worksFor [
      a schema:Organization;
      schema:name "My Organization";
	  schema:address [
        a schema:PostalAddress ;
        schema:streetAddress "Some Other Road 999";
        schema:addressLocality "Random City";
        schema:postalCode "88888";
        schema:addressCountry "Country";
      ]
    ].'
```

## Running the demo application

First, you can fetch the Shape file and generate a GraphQL schema:

```
./gradlw sdxBuild
```
