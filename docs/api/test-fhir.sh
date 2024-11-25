#!/usr/bin/env sh

curl -X POST -H "Content-Type: application/fhir+xml" http://localhost:8085/fhir --data-binary @- <<EOF
<Bundle xmlns="http://hl7.org/fhir" xmlns:hash="java:de.samply.obds2fhir">
   <id value="653ae947f9c1f8f6"/>
   <type value="transaction"/>
   <entry>
      <fullUrl value="http://example.com/Patient/653ae947f9c1f8f6"/>
      <resource>
         <Patient>
            <id value="653ae947f9c1f8f6"/>
            <meta>
               <profile value="http://dktk.dkfz.de/fhir/StructureDefinition/onco-core-Patient-Patient"/>
            </meta>
            <identifier>
               <type>
                  <coding>
                     <system value="http://dktk.dkfz.de/fhir/onco/core/CodeSystem/PseudonymArtCS"/>
                     <code value="Lokal"/>
                  </coding>
               </type>
               <value value="d9fa1d5a291110d0e48ba42d732cce11"/>
            </identifier><!--
            <gender value="male"/>
            <birthDate value="1980-07-03"/>-->
         </Patient>
      </resource>
      <request>
         <method value="PUT"/>
         <url value="Patient/653ae947f9c1f8f6"/>
      </request>
   </entry>
   <entry>
      <fullUrl value="http://example.com/Specimen/bio08c57d26db215355"/>
      <resource>
         <Specimen>
            <id value="bio08c57d26db215355"/>
            <meta>
               <profile value="https://fhir.bbmri.de/StructureDefinition/Specimen"/>
            </meta>
            <type>
               <coding>
                  <system value="https://fhir.bbmri.de/CodeSystem/SampleMaterialType"/>
                  <code value="tissue-ffpe"/>
               </coding>
            </type>
            <subject>
               <reference value="Patient/identifier=d9fa1d5a291110d0e48ba42d732cce11"/>
            </subject>
         </Specimen>
      </resource>
      <request>
         <method value="PUT"/>
         <url value="Specimen/bio08c57d26db215355"/>
      </request>
   </entry>
</Bundle>
EOF

curl -X POST -H "Content-Type: application/fhir+xml" http://localhost:8086/fhir --data-binary @- <<EOF
<Bundle xmlns="http://hl7.org/fhir" xmlns:hash="java:de.samply.obds2fhir">
   <id value="653ae947f9c1f8f6"/>
   <type value="transaction"/>
   <entry>
      <fullUrl value="http://example.com/Patient/653ae947f9c1f8f6"/>
      <resource>
         <Patient>
            <id value="653ae947f9c1f8f6"/>
            <meta>
               <profile value="http://dktk.dkfz.de/fhir/StructureDefinition/onco-core-Patient-Patient"/>
            </meta>
            <identifier>
               <type>
                  <coding>
                     <system value="http://dktk.dkfz.de/fhir/onco/core/CodeSystem/PseudonymArtCS"/>
                     <code value="Lokal"/>
                  </coding>
               </type>
               <value value="d9fa1d5a291110d0e48ba42d732cce11"/>
            </identifier><!--
            <gender value="male"/>
            <birthDate value="1980-07-03"/>-->
         </Patient>
      </resource>
      <request>
         <method value="PUT"/>
         <url value="Patient/653ae947f9c1f8f6"/>
      </request>
   </entry>
   <entry>
      <fullUrl value="http://example.com/Specimen/bio08c57d26db215355"/>
      <resource>
         <Specimen>
            <id value="bio08c57d26db215355"/>
            <meta>
               <profile value="https://fhir.bbmri.de/StructureDefinition/Specimen"/>
            </meta>
            <type>
               <coding>
                  <system value="https://fhir.bbmri.de/CodeSystem/SampleMaterialType"/>
                  <code value="tissue-ffpe"/>
               </coding>
            </type>
            <subject>
               <reference value="Patient/653ae947f9c1f8f6"/>
            </subject>
         </Specimen>
      </resource>
      <request>
         <method value="PUT"/>
         <url value="Specimen/bio08c57d26db215355"/>
      </request>
   </entry>
</Bundle>
EOF


curl -X POST -H "Content-Type: application/fhir+xml" http://localhost:8090/fhir --data-binary @- <<EOF
<Bundle xmlns="http://hl7.org/fhir" xmlns:hash="java:de.samply.obds2fhir">
   <id value="653ae947f9c1f8f6"/>
   <type value="transaction"/>
   <entry>
      <fullUrl value="http://example.com/Patient/653ae947f9c1f8f6"/>
      <resource>
         <Patient>
            <id value="653ae947f9c1f8f6"/>
            <meta>
               <profile value="http://dktk.dkfz.de/fhir/StructureDefinition/onco-core-Patient-Patient"/>
            </meta>
            <identifier>
               <type>
                  <coding>
                     <system value="http://dktk.dkfz.de/fhir/onco/core/CodeSystem/PseudonymArtCS"/>
                     <code value="Lokal"/>
                  </coding>
               </type>
               <value value="d9fa1d5a291110d0e48ba42d732cce11"/>
            </identifier><!--
            <gender value="male"/>
            <birthDate value="1980-07-03"/>-->
         </Patient>
      </resource>
      <request>
         <method value="PUT"/>
         <url value="Patient/653ae947f9c1f8f6"/>
      </request>
   </entry>
   <entry>
      <fullUrl value="http://example.com/Specimen/bio08c57d26db215355"/>
      <resource>
         <Specimen>
            <id value="bio08c57d26db215355"/>
            <meta>
               <profile value="https://fhir.bbmri.de/StructureDefinition/Specimen"/>
            </meta>
            <type>
               <coding>
                  <system value="https://fhir.bbmri.de/CodeSystem/SampleMaterialType"/>
                  <code value="tissue-ffpe"/>
               </coding>
            </type>
            <subject>
               <identifier>
               <system value="http://dktk.dkfz.de/fhir/onco/core/CodeSystem/PseudonymArtCS"/>
               <value value="d9fa1d5a291110d0e48ba42d732cce11"/>
               </identifier>
            </subject>
         </Specimen>
      </resource>
      <request>
         <method value="PUT"/>
         <url value="Specimen/bio08c57d26db215355"/>
      </request>
   </entry>
</Bundle>
EOF
