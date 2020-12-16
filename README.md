# Generic Bank Take-Home Coding Exercises

## What to expect?
We understand that your time is valuable, and in anyone's busy schedule solving these exercises may constitute a fairly 
substantial chunk of time, so we really appreciate any effort you put in and hope to also offer you the opportunity
to learn something new.

## What we are looking for?
**Keep it simple**. Read the requirements and restrictions carefully and focus on solving the problem. The goal
is not to keep you busy for several days.

**Treat it like production code**. Develop your software in the same way that you would for production code. 
We really would like to get an idea of how you build code on a day-to-day basis.

## What to submit?
Make sure your submission includes a small **README**, documenting any assumptions, simplifications and/or choices 
you made, as well as a short description of how to run the code and/or tests. 

It would be great if you could also add a sensible git-commit history with your solution. This helps us to follow
your thought process better.

## The Interview:
After you submit your code, we will contact you to discuss and potentially arrange an in-person interview with 
some members of the team.
The interview will cover a wide range of technical and social aspects relevant to working at our Generic Bank. But importantly 
for this exercise, we will also take the opportunity to step through your submitted code with you.

## The Exercise:

### What is SWIFT?

> The Society for Worldwide Interbank Financial Telecommunication (SWIFT), legally S.W.I.F.T. SCRL, provides a network
> that enables financial institutions worldwide to send and receive information about financial transactions in a 
> secure, standardized and reliable environment. SWIFT also sells software and services to financial institutions, 
> much of it for use on the SWIFTNet network, and ISO 9362. Business Identifier Codes (BICs, previously Bank 
> Identifier Codes) are popularly known as "SWIFT codes".

### What is Generic Bank System?
Our Generic Bank System handles incoming and outgoing SWIFT messages. It checks the content of 
financial messages, enriches the messages by calling external systems such as fraud-checks, and finally routes
the message to any bank worldwide based on the enrichment process.

During the above mentioned processes, it also persists and indexes the messages to allow users to track their
SWIFT messages or execute audit checks.

### Problem Description

> A stream of MT103 and MT202 messages arrives into our system. Each message goes through the following flow:
> 1. Parsing  - The information from the message needs to be extracted and validated
> 2. Fraud    - We need to call an external system to check if this message has been flagged for fraud or not
> 3. Outgoing - All messages are eventually send to an external system. In this case, the console.

This means:
1. We give you an `IncomingStream` that is an AkkaStream of `File`s. Each `File` is a financial message in a JSON format.
2. A `MessageParser` trait is given, it should transform a `File` into a `FinancialMessage`.
3. Each `FinancialMessage` needs to be checked for fraud by the `FraudClient`. The `FraudClient` calls the given
`FraudApi` and simulates an external API call.
4. Based on the result from the `FraudClient`, we enrich the `FinancialMessage` field `isFraud`.
5. Eventually, we show the successful `FinancialMessage`s on the console as a final step.

Some notes:
- The given JSON files do **NOT** follow SWIFT specifications. 
- The given JSON files do use the matching concepts of specific fields described in the SWIFT documentation.
- You can find more information about an MT103 message in the [SWIFT documentation](
https://www2.swift.com/knowledgecentre/publications/us1m_20200724/1.0?topic=mt103-field-spec.htm)
- You can find more information about an MT202 message in the [SWIFT documentation](
https://www2.swift.com/knowledgecentre/publications/us2m_20200724/1.0?topic=mt202-field-spec.htm)
- Don't feel limited by the existing dependencies; you can include others.
- The algebras/interfaces provided act as an example/starting point. Feel free to add/change to improve or built on it when needed.
- It's great for your colleagues if the methods have descriptive errors in case something goes wrong.

## F.A.Q.
1) _Should I do X?_
For any value of X, it is up to you. We intentionally leave the problem a little open-ended and will leave it up to 
you to provide us with what you see as important. We stress again, please keep it simple. If it's a feature that is going to 
take you a couple of hours, it's not essential.

3) _Something is ambiguous, and I don't know what to do?_
The first thing is: don't get stuck. We really don't want to trip you up intentionally, we are just attempting to see 
how you approach problems. 
If you really feel stuck, our first preference is for you to make a decision and document it with your submission - in 
this case there is really no wrong answer. 
If you feel it is not possible to do this, just send us an email and we will try to clarify or correct the question for you.

Good luck!


#### Sources
This README is based of the wonderful README.md from Paidy's interview exercise.

# Implementation notes

## Handling incoming message files
### MT103
* 33B—Currency/Instructed Amount
  * Mandatory
  * Conforms to ISO 4217 or fails with T52.
  * Validates to at least one integer digit, comma and trailing fraction. Given vague (Error code(s): C03, T40, T43) in the spec we'll skip this validation. BigDecimal is good enough.
* 50A—Ordering Customer
  * Mandatory, but missing in the domain model
  * This field specifies the customer ordering the transaction. 
* 51A—Sending Institution
  * Optional, but is mandatory in the domain model
  * Though it 
* 57A—Account with Instruction
  * The presence is conditional, so optional in terms if input parsing. But is mandatory in the domain model
* 59A—Beneficiary Customer
  * Mandatory, but is absent in the domain model 
  
### MT202
* 32A—Value Date/Currency/Amount
  * Mandatory
  * The test data lack Value Date part, so let it be like that
* 52A—Ordering institution
  * Optional, but is mandatory in the data model.
* 58A—Beneficiary Institution
  * Mandatory and mandatory in the domain model
  
Given the aforementioned and the contents of the MT103- and MT202-like files I'll map BICs of the messages to
`sender` and `receiver` fields of the `FinancialMessage` class respectively and treat all of them mandatory including 
the `amount`. Thus abcense of any of those attributes in the file messages is treated as an error in the domain.

### Errors in the domain:
I can foresee:
* Invalid field format
* Missing expected field

Revisit this section after the `MessageParser` implementation.

### Parser
* I'm used to Circe for handling JSON, so I'll go with it.
* It is quite unhandy to test something with a File as input, so the implementation of `MessageParser` consists of tiny flat-mappable steps
* `parseBic` might look weird, but it is the way to guarantee we do not mismatch sender and receiver while wrangling BICs.

Revisited parsing errors:
* `UnsupportedMessageType` if `messageType` differs from `MT103` and `MT202`
* `ParseFailure` if the file content can't be converted to JSON
* `MissingField` as above: missing mandatory field in JSON message
* `FieldFormatFailure` when the field doesn't meet criteria
* `LoadFailure` if we cannot load the file content as a string

## Fraud Client
### Design
FraudClient implementation is not a big deal: it is provided with a function from `FinancialMessage` to a
`Future[Either[api.Error, api.domain.Result]]` which is easy to adopt by our client domain.
Plus there comes an extra client error type `ClientFailure` to denote client side failures such as execution context exceptions
and so on.

## Runtime/Main implementation
### Akka Streams are far away from being functionally idiomatic, I'll try to mimic binds chain and laziness.
I'm taking responsibility of changing `Either[Error, Source[File, NotUsed]]` to `Source[Either[Error, File], NotUsed]`
in the IncomingSource API. Looks more natural as s Source of multiple files.