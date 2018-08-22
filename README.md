# Batch Processing

Spring Batch provides reusable functions that are essential in processing large volumes of records, including 
logging/tracing, transaction management, job processing statistics, job restart, skip, and resource management. 
It also provides more advanced technical services and features that will enable extremely high-volume and high 
performance batch jobs through optimization and partitioning techniques. Simple as well as complex, high-volume 
batch jobs can leverage the framework in a highly scalable manner to process significant volumes of information.

## Data to process

A spreadsheet was create that contains a first name, last name, age, email and an ip address on each row, separated by a comma. 
This is a fairly common pattern that Spring handles out-of-the-box.

      src/main/resources/sample-data.csv
      
      Calvin,Robet,crobet0@vk.com,Male,191.33.174.203
      Kathryn,Nowaczyk,knowaczyk1@eventbrite.com,Female,145.53.50.38
      Sandi,Perle,sperle2@amazon.com,Female,29.212.217.105
      Kerianne,Piers,kpiers3@ox.ac.uk,Female,194.35.7.62

Next, you write a SQL script to create a table to store the data.
    
      src/main/resources/schema-all.sql
      
      DROP TABLE person IF EXISTS;
      
      CREATE TABLE person  (
          person_id BIGINT IDENTITY NOT NULL PRIMARY KEY,
          first_name VARCHAR(20),
          last_name VARCHAR(20),
          email VARCHAR(50),
          gender VARCHAR(10),
          ip_address VARCHAR(20)
      );
      
      
      Spring Boot runs schema-@@platform@@.sql automatically 
      during startup. -all is the default for all platforms.
  
## Intermediate Processor
      
A common paradigm in batch processing is to ingest data, transform it, and then pipe it out somewhere else. 
Here you write a simple transformer that converts the names to uppercase.

      src\main\java\com\hcl\cnp\batchprocessing\service\PersonItemProcessor.java
      
      public class PersonItemProcessor implements ItemProcessor<Person, Person> {
      
          public static final Logger log = LoggerFactory.getLogger(PersonItemProcessor.class);
      
          @Override
          public Person process(final Person person) throws Exception {
      
              final String firstName = person.getFirstName().toUpperCase();
              final String lastName = person.getLastName().toUpperCase();
      
              final Person transformedPerson = new Person(firstName, lastName, person.getEmail(), person.getGender(), person.getIpAddress());
      
              log.info("Transforming: ( " + person + " )into (" + transformedPerson + " )");
              log.info(String.valueOf(person));
      
              return transformedPerson;
          }
      }
      
**PersonItemProcessor** implements Spring Batch’s **ItemProcessor** interface. This makes it easy to wire the code into a 
batch job that you define further down in this guide. According to the interface, you receive an incoming **Person** object, 
after which you transform it to an upper-cased **Person**

## Batch Job
     
     src\main\java\com\hcl\cnp\batchprocessing\BatchConfiguration.java
     
     @Configuration
     @EnableBatchProcessing
     public class BatchConfiguration {
        .
        .
        .
     }

The @EnableBatchProcessing annotation adds many critical beans that support jobs and saves you a lot of 
leg work. This example uses a memory-based database (provided by @EnableBatchProcessing), meaning that when it’s done, 
the data is gone.

       src\main\java\com\hcl\cnp\batchprocessing\BatchConfiguration.java

       // tag::readerwriterprocessor[]
       @Bean
       public FlatFileItemReader<Person> reader() {
           return new FlatFileItemReaderBuilder<Person>()
                   .name("personItemReader")
                   .resource(new ClassPathResource("sample-data.csv"))
                   .delimited()
                   .names(new String[]{"firstName", "lastName", "email", "gender", "ipAddress"})
                   .fieldSetMapper(new BeanWrapperFieldSetMapper<Person>() {{
                       setTargetType(Person.class);
                   }})
                   .build();
       }
   
       @Bean
       public PersonItemProcessor processor() {
           return new PersonItemProcessor();
       }
   
       @Bean
       public JdbcBatchItemWriter<Person> writer(DataSource dataSource) {
           return new JdbcBatchItemWriterBuilder<Person>()
                   .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                   .sql("INSERT INTO person (first_name, last_name, email, gender, ip_address) VALUES (:firstName, :lastName, :email, :gender, :ipAddress)")
                   .dataSource(dataSource)
                   .build();
       }
       // end::readerwriterprocessor[]

This first chunk of code defines the input, processor, and output. - reader() creates an ItemReader. It looks for a file called 
sample-data.csv and parses each line item with enough information to turn it into a Person. - processor() creates an instance 
of our PersonItemProcessor you defined earlier, meant to uppercase the data. - write(DataSource) creates an ItemWriter. 
This one is aimed at a JDBC destination and automatically gets a copy of the dataSource created by @EnableBatchProcessing. 
It includes the SQL statement needed to insert a single Person driven by Java bean properties.

         @Bean
         public Job importUserJob(JobCompletionNotificationListener listener, Step step1) {
             return jobBuilderFactory.get("importUserJob")
                 .incrementer(new RunIdIncrementer())
                 .listener(listener)
                 .flow(step1)
                 .end()
                 .build();
         }
     
         @Bean
         public Step step1(JdbcBatchItemWriter<Person> writer) {
             return stepBuilderFactory.get("step1")
                 .<Person, Person> chunk(10)
                 .reader(reader())
                 .processor(processor())
                 .writer(writer)
                 .build();
         }
         
         chunk() is prefixed <Person,Person> because it’s a generic method. 
         This represents the input and output types of each "chunk" of processing, 
         and lines up with ItemReader<Person> and ItemWriter<Person>.
         
The first method defines the job and the second one defines a single step. Jobs are built from steps, where each step 
can involve a reader, a processor, and a writer.

In this job definition, you need an incrementer because jobs use a database to maintain execution state. 
You then list each step, of which this job has only one step. The job ends, and the Java API produces a perfectly configured job.

In the step definition, you define how much data to write at a time. In this case, it writes up to ten records at a time. 
Next, you configure the reader, processor, and writer using the injected bits from earlier.

        
        @Component
        public class JobCompletionNotificationListener extends JobExecutionListenerSupport {
        
          private static final Logger log = LoggerFactory.getLogger(JobCompletionNotificationListener.class);
         
          private final JdbcTemplate jdbcTemplate;
         
          @Autowired
          public JobCompletionNotificationListener(JdbcTemplate jdbcTemplate) {
           this.jdbcTemplate = jdbcTemplate;
          }
        
          //This code listens for when a job is BatchStatus.COMPLETED, 
          //and then uses JdbcTemplate to inspect the results.
         
          @Override
          public void afterJob(JobExecution jobExecution) {
           if(jobExecution.getStatus() == BatchStatus.COMPLETED) {
              log.info("!!! JOB FINISHED! Time to verify the results");
           
              jdbcTemplate.query("SELECT first_name, last_name, email, gender, ip_address FROM person",
                (rs, row) -> new Person(
                  rs.getString(1),
                  rs.getString(2),
                  rs.getString(3),
                  rs.getString(4),
                  rs.getString(5))
              ).forEach(person -> log.info("Found <" + person + "> in the database."));
           }
          }
        
        }