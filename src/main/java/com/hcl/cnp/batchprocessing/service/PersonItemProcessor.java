package com.hcl.cnp.batchprocessing.service;

import com.hcl.cnp.batchprocessing.domain.Person;
import org.springframework.batch.item.ItemProcessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Tech Support on 8/20/2018.
 */
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
