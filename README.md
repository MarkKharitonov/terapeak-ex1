Ex 1
========

 - The DB used is postgresql.
 - Changed the password of the postgres user to 123. I should have created a new user, but I think for the exercise it is good enough.
 - Again, for the sake of the simplicity the password and the user are hard coded in the source code.
 - Created exdb database using the mkdb.sh script.
 - Using the Public schema.
 - Use the reset.sql script to create the table **before** running the application - `./psql.sh -f reset.sql`.
 - Running the script **erases the old table**, so beware!
 - I did not spend much time doing error handling, something I would have surely done in a real application.
 - The code comes with an ant build script, just run ant without any arguments and find ex1.jar under bin\deploy.
 - `java -jar bin\deploy\ex1.jar -f ../input_data_sample.json.gz`

Ex 2
========

 - The keywords are calculated by splitting the description using the following regex: `"\\s+|\\)|\\(|\\||\\?|\\!|\\\"|\\.|;|,|\\s+[':&-]|[':&-]\\s+"`
 - Some keywords are ignored, namely these (case insensitive): "a", "the", "of", "per", "to", "this", "an", "with", "for", "and", "in", "any",
   "not", "only", "does", "do", "but", "you", "can", "or", "on", "is", "from", "your", "may", "so".
 - The proper way of handling such filtering is by creating a component which would receive a string and spit out keywords. Then this
   component should be injected into the relevant place using some DI. However, this is too much for an exercise.
 - Some keywords are too long, in which case they are truncated and terminated with an ellipsis. An alternative would be to add another
   boolean column explicitly indicating which keywords were truncated. Again, the proper handling should be implemented with an injectable
   component. The max size of the keyword is determined by reading the database metadata - no magic numbers in the code (only magic strings).
   Or we could simply give the keyword column the same size as that of the description (10,000).
 - The keywords table is de-normalized. Meaning it contains the name in full rather than a nameId foreign key into the items table.
 - Make sure to reset the database - `./psql.sh -f reset.sql`
 - The query uses exact match on the keyword field. If a partial match is needed we can use the LIKE SQL statement. This could be a command
   line argument...
 - One db insertion thread is not enough - too slow. Use the following command line:
   `java -jar bin\deploy\ex1.jar -f ../input_data_sample.json.gz --dbThreadCount 5`
 - To query: `java -jar bin/deploy/ex1.jar --query visual`
