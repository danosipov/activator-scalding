# Activator Scalding Template

## Explore Scalding

This template demonstrates how to build and run [Scalding](https://github.com/twitter/scalding)-based *Big Data* applications for [Hadoop](http://hadoop.apache.org). You can also run them "locally" on your personal machine, which we will do here, for convenient development and testing. Actually, these "applications" are more like "scripts" than traditional, multi-file applications.

[Scalding](https://github.com/twitter/scalding) is a Scala API developed at Twitter for distributed data programming that sits on top of the [Cascading](http://www.cascading.org/) Java API, which in turn sits on top of Hadoop's Java API. However, through Cascading, Scalding also offers a *local* mode that makes it easy to run jobs without Hadoop. This greatly simplifies and accelerates learning and testing of applications. It's even "good enough" for small data sets that fit easily on a single machine. 

## Building and Running

Invoke <a class="shortcut" href="#run">run</a> to try it out. The default `main` class `RunAll` runs all of the scripts with default arguments. Each script also has its own `main` routine that you can select in the drop-down menu on the Run panel. The four examples provided are these:

* **NGrams:** find all N-word ("N-gram") occurrences matching a pattern. In this case, the 4-word phrases in the King James Version of the Bible of the form "% love % %", where the "%" are wild cards. In other words, all 4-grams are found with "love" as the second word. There are 5 such NGrams.
* **WordCount:** find all the words in a corpus of documents and count them, again using the KJV.
* **FilterUniqueCountLimit:** demonstrate how to filter records (we'll create a "skeptics Bible" that removes all verses with the word "miracle"; like SQL's `WHERE` clause), how to find unique values (we'll find the names of the books of the KJV; like SQL's `DISTINCT` keyword), how to count all records (we'll count the total number of verses in the KJV; like SQL's `COUNT (*)` clause), and how to limit the number of records (we'll return the first 10 verses; like SQL's `LIMIT N` clause).
* **TfIdf:** compute the *term frequency-inverse document frequency* of the KJV, an algorithm used in part to create search indices for the web or document corpi.

Each of these scripts writes output to the `output` directory, but for convenience, we echo some of output to the Activator window.

Let's examine these scripts in more detail...

## NGrams

Let's see how the *NGrams* Script works. Open <a class="shortcut" href="#code/src/main/scala/scalding/NGrams.scala">NGrams.scala</a>. 

In the Run panel, select *NGrams* from the drop-down menu to invoke this script by itself.

Here is the entire script, with the comments removed:

```
import com.twitter.scalding._

class NGrams(args : Args) extends Job(args) {
  
  val ngramsArg = args.list("ngrams").mkString(" ").toLowerCase
  val ngramsRE = ngramsArg.trim
    .replaceAll("%", """ (\\p{Alnum}+) """)
    .replaceAll("""\s+""", """\\p{Space}+""").r
  val numberOfNGrams = args.getOrElse("count", "20").toInt

  val countReverseComparator = 
    (tuple1:(String,Int), tuple2:(String,Int)) => tuple1._2 > tuple2._2
      
  val lines = TextLine(args("input"))
    .read
    .flatMap('line -> 'ngram) { 
      text: String => ngramsRE.findAllIn(text.trim.toLowerCase).toIterable 
    }
    .discard('offset, 'line)
    .groupBy('ngram) { _.size('count) }
    .groupAll { 
      _.sortWithTake[(String,Int)](
        ('ngram,'count) -> 'sorted_ngrams, numberOfNGrams)(countReverseComparator)
    }
    .debug
    .write(Tsv(args("output")))
}
```

Let's walk through this code. 

```
import com.twitter.scalding._

class NGrams(args : Args) extends Job(args) {
  ...
```

We start with the Scalding imports we need, then declare a class `NGrams` that subclasses a `Job` class, which provides a `main` routine and other runtime context support (such as Hadoop integration). Our class must take a list of command-line arguments, which are processed for us by Scalding's `Args` class. We'll use these to specify where to find input, where to write output, and handle other configuration options.

```
  ...
  val ngramsArg = args.list("ngrams").mkString(" ").toLowerCase
  val ngramsRE = ngramsArg.trim
    .replaceAll("%", """ (\\p{Alnum}+) """)
    .replaceAll("""\s+""", """\\p{Space}+""").r
  val numberOfNGrams = args.getOrElse("count", "20").toInt
  ...
```

Before we create our *dataflow*, a series of *pipes* that provide data processing, we define a values that we'll need. The user specifies the NGram pattern they want, such as the "% love % %" used in our *run* example. The `ngramsRE` takes that NGram specification and turns it into a regular expression that we need. The "%" are converted into patterns to find any word and any runs of whitespace are generalized for all whitespace. Finally, we get the command line argument for the number of most frequently occurring NGrams to find, which defaults to 20 if not specified.

```
  ...
  val countReverseComparator = 
    (tuple1:(String,Int), tuple2:(String,Int)) => tuple1._2 > tuple2._2
  ...
```

The `countReverseComparator` function will be used to rank our found NGrams by frequency of occurrence, descending. The count of occurrences will be the second field in each tuple.


```
  ...
  val lines = TextLine(args("input"))
    .read
    .flatMap('line -> 'ngram) { 
      text: String => ngramsRE.findAllIn(text.trim.toLowerCase).toIterable 
    }
    .discard('offset, 'line)
    ...
```

Now our dataflow is created. A `TextLine` object is used to read each "record", a line of text as a single "field". Hence, the records are newline (`\n`) separated. It reads the file specified by the `--input` argument (processed by the `args` object). 

For input, we use a file containing the King James Version of the Bible. We have included that file; see the `data/README` file for more information.

Each line of the input actually has the following *schema*:

```
Abbreviated name of the book of the Bible (e.g., Gen) | chapter | verse | text
```

For example, this the very first (famous) line:

```
Gen|1|1| In the beginning God created the heaven and the earth.
```

Note that a flaw with our implementation is that NGrams across line boundaries won't be found, because we process each line separately. However, the text for the King James Version of Bible that we are using has each verse on its own line. It wouldn't make much sense to compute NGrams across verses, so this limitation is not an issue for this particular data set.

Next, we call `flatMap` on each line record, converting it to zero or more output records, one per NGram found. Of course, some lines won't have a matching NGram. We use our regular expression to tokenize each line, and also trim leading and trailing whitespace and convert to lower case. 

A scalding API convention is to use the first argument list to a function to specify the field names to input to the function and name the new fields output. In this case, we input just the line field, named `'line` (a Scala *symbol*) and name each found NGram `'ngram`. Note who these field names are specified using a tuple.

Finally in this section, we discard the fields we no longer need. Operations like `flatMap` and `map` append the new fields to the existing fields. We no longer need the `'line` and `TextLine` also added a line number field to the input, named `'offset`. 

```
    ...
    .groupBy('ngram) { _.size('count) }
    .groupAll { 
      _.sortWithTake[(String,Int)](
        ('ngram,'count) -> 'sorted_ngrams, numberOfNGrams)(countReverseComparator)
    }
    ...
}

```

If we want to rank the found NGrams by their frequencies, we need to get all occurrences of a given NGram together. Hence, we use a `groupBy` operation to group over the `'ngram` fields. To sort and output the tope `numberOfNGrams`, we group *all* together, then use a special Scalding function that combines sorting with "taking", i.e., just keeping the top N values after sorting.


```
    ...
    .debug
    .write(Tsv(args("output")))
}
```

The `debug` function dumps the current stream of data to the console, which is useful for debugging. Don't do this for massive data sets!!

Finally, we write the results as tab-separated values to the location specified by the `--output` command-line argument.

To recap, look again at the whole listing above. It's not very big! For what it does and compared to typical code bases you might work with, this is incredibly concise and powerful code.

*WordCount* is next...

## WordCount

Open <a class="shortcut" href="#code/src/main/scala/scalding/WordCount.scala">WordCount.scala</a>, which implements the well-known *Word Count* algorithm, which is popular as an easy-to-implement, "hello world!" program for developers learning Hadoop.

In *WordCount*, a corpus of documents is read, the contents are tokenized into words, and the total count for each word over the entire corpus is computed. The output is sorted by frequency descending.

In the Run panel, select *WordCount* from the drop-down menu to invoke this script by itself.

Here is the script without comments:

```
import com.twitter.scalding._

class WordCount(args : Args) extends Job(args) {

  val tokenizerRegex = """\W+"""
  
  TextLine(args("input"))
    .read
    .flatMap('line -> 'word) {
      line : String => line.trim.toLowerCase.split(tokenizerRegex) 
    }
    .groupBy('word){ group => group.size('count) }
    .write(Tsv(args("output")))
}
```

Each line is read as plain text from the input location specified by the `--input` argument, just as we did for *NGrams*. 

Next, `flatMap` is used to tokenize the line into words, similar to the first few steps in *NGrams*.

Next, we group over the words, to get all occurrences of each word gathered together, and we compute the size of each group, naming this size field `'count'. 

Finally, we write the output `'word` and `'count` fields as tab-separated values to the location specified with the `--output` argument, as for *NGrams*.

*FilterUniqueCountLimit* is next...

## FilterUniqueCountLimit

Open <a class="shortcut" href="#code/src/main/scala/scalding/FilterUniqueCountLimit.scala">FilterUniqueCountLimit.scala</a>, which shows a few useful techniques:

1. How to split a data stream into several flows, each for a specific calculation.
2. How to filter records (like SQL's `WHERE` clause).
3. How to find unique values (like SQL's `DISTINCT` keyword).
4. How to count all records (like SQL's `COUNT(*)` clause).
5. How to limit output (like SQL's `LIMIT n` clause).

In the Run panel, select *FilterUniqueCountLimit* from the drop-down menu to invoke this script by itself.

Here is the full script without comments:

```
import com.twitter.scalding._

class FilterUniqueCountLimit(args : Args) extends Job(args) {

  val kjvSchema = ('book, 'chapter, 'verse, 'text)
  val outputPrefix = args("output")

  val bible = Csv(args("input"), separator = "|", fields = kjvSchema)
      .read

  new RichPipe(bible)
      .filter('text) { t:String       => t.contains("miracle") == false }
      .write(Csv(s"$outputPrefix-skeptic.txt", separator = "|"))

  new RichPipe(bible)
      .project('book)
      .unique('book)
      .write(Tsv(s"$outputPrefix-books.txt"))  

  new RichPipe(bible)
      .groupAll { _.size('countstar).reducers(2) }
      .write(Tsv(s"$outputPrefix-count-star.txt"))  

  new RichPipe(bible)
      .limit(args.getOrElse("n", "10").toInt)
      .write(Csv(s"$outputPrefix-limit-N.txt", separator = "|"))
}
```

This time, we read each line ("record") of text as a "|"-separated fields with the fields named by the `kjvSchema` value. Each input line is a verse in the Bible. We also treat the `--output` argument as a prefix, because four separate files will be output this time.

We open the KJV file using a comma-separated values reader, but overriding the separator to be "|" and applying the `kjvSchema` specification to each record.

Now we clone this input pipe four times to do four separated operations on the data. The first pipe filters each line, removing those with the word *miracle*, thus creating a "skeptics Bible". (Thomas Jefferson could have used this feature...) The output is written to file with the name suffix `-skeptic.txt`.

The second pipe projects just the first column/field, the name of the book of the Bible and finds all the unique values for this field, thereby producing a list of books in the Bible.

The third pipe uses the `groupAll` idiom to collect all records together and count them, yielding the total number of verses in the KJV Bible, 31102.

The fourth and final pipe limits the number of records to the input value given for the `--n` argument or 10 if the argument isn't specified. Hence, it's output is just the first n lines of the KJV.

*TfIdf* is our last example script...

## TfIdf

Open <a class="shortcut" href="#code/src/main/scala/scalding/TfIdf.scala">TfIdf.scala</a>, our most complex example script. It implements the *term frequency-inverse document frequency* algorithm used as part of  the indexing process for document or Internet search engines. (See [this Wikipedia page](http://en.wikipedia.org/wiki/Tf*idf) for more information on this algorithm.)

In the Run panel, select *TfIdf* from the drop-down menu to invoke this script by itself.

In a conventional implementation of Tf-Idf, you might load a precomputed document to word matrix: 

```
a[i,j] = frequency of the word j in the document with index i 
```

Then, you would compute the Tf-Idf score of each word with respect to each document.

Instead, we'll compute this matrix by first performing a modified *Word Count* on our KJV Bible data, then convert that data to a matrix and proceed from there. The modified *Word Count* will track the source Bible book and `groupBy` the `('book, 'word)` instead of just the `'word`.

Here is the entire script without comments:

```
import com.twitter.scalding._
import com.twitter.scalding.mathematics.Matrix

class TfIdf(args : Args) extends Job(args) {
  
  val n = args.getOrElse("n", "100").toInt 
  val kjvSchema = ('book, 'chapter, 'verse, 'text)
  val tokenizerRegex = """\W+"""
  
  val books = Vector(
    "Act", "Amo", "Ch1", "Ch2", "Co1", "Co2", "Col", "Dan", "Deu", 
    "Ecc", "Eph", "Est", "Exo", "Eze", "Ezr", "Gal", "Gen", "Hab", 
    "Hag", "Heb", "Hos", "Isa", "Jam", "Jde", "Jdg", "Jer", "Jo1", 
    "Jo2", "Jo3", "Job", "Joe", "Joh", "Jon", "Jos", "Kg1", "Kg2", 
    "Lam", "Lev", "Luk", "Mal", "Mar", "Mat", "Mic", "Nah", "Neh", 
    "Num", "Oba", "Pe1", "Pe2", "Phi", "Plm", "Pro", "Psa", "Rev", 
    "Rom", "Rut", "Sa1", "Sa2", "Sol", "Th1", "Th2", "Ti1", "Ti2",
    "Tit", "Zac", "Zep")
  
  val booksToIndex = books.zipWithIndex.toMap
  
  val byBookWordCount = Csv(args("input"), separator = "|", fields = kjvSchema)
    .read
    .flatMap('text -> 'word) {
      line : String => line.trim.toLowerCase.split(tokenizerRegex) 
    }
    .project('book, 'word)
    .map('book -> 'bookId)((book: String) => booksToIndex(book))
    .groupBy(('bookId, 'word)){ group => group.size('count) }

  import Matrix._

  val docSchema = ('bookId, 'word, 'count)

  val docWordMatrix = byBookWordCount
    .toMatrix[Long,String,Double](docSchema)

  val docFreq = docWordMatrix.sumRowVectors

  val invDocFreqVct = 
    docFreq.toMatrix(1).rowL1Normalize.mapValues( x => log2(1/x) )

  val invDocFreqMat = 
    docWordMatrix.zip(invDocFreqVct.getRow(1)).mapValues(_._2)

  val out1 = docWordMatrix.hProd(invDocFreqMat).topRowElems(n)
    .pipeAs(('bookId, 'word, 'frequency))
    .mapTo(('bookId, 'word, 'frequency) -> ('book, 'word, 'frequency)){
      tri: (Int,String,Double) => (books(tri._1), tri._2, tri._3)
    }

  val abbrevToNameFile = args.getOrElse("abbrevs-to-names", "data/abbrevs-to-names.tsv")
  val abbrevToName = Tsv(abbrevToNameFile, fields = ('abbrev, 'name)).read

  out1.joinWithTiny('book -> 'abbrev, abbrevToName)
    .project('name, 'word, 'frequency)
    .write(Tsv(args("output")))

  def log2(x : Double) = scala.math.log(x)/scala.math.log(2.0)
}

```

This example uses Scalding's Matrix API, which simplifies working with "sparse" matrices.

Here, the `--n` argument is used to specify how many of the most frequently-occurring terms to keep for each book of the Bible. It defaults to 100.

We use the same input schema and word-tokenization we used previously for *FilterUniqueCountLimit* and *WordCount*, respectively.

We'll need to convert the Bible book names to numeric ids. We could actually compute the unique books and assign each an id (as discussed for *FilterUniqueCountLimit*), but to simplify things, we'll simply hard-code the abbreviated names used in the KJV text file and then zip this collection with the corresponding indices to create ids.

The `byBookWordCount` pipeline is very similar to *WordCount*, but we don't forget which book the word came from, so our key for grouping is now the `'booked` and the `'word`.

Next, we convert `byBookWordCount` to a term frequency, two-dimensional matrix, using Scalding Matrix API, where the "x" coordinate is the book id, the "y" coordinate is the word, and the value is the count, converted to `Double`.

Now we compute the overall document frequency of each word. The value of `docFreq(i)` will be the total count for word `i` over all documents. Then we need the inverse document frequency vector, which is used to suppress the significance of really common words, like "the", "and", "I", etc. The *L1 norm* is just `1/(|a| + |b| + ...)`, rather then the square root of the sum of squares, which would be more accurate, but also more expensive to compute. Actually, we use `1/log(x)`, rather than `1/x`, for better numerical stability. 

Then we zip the row vector along the entire document-word matrix and multiply the term frequency with the inverse document frequency, keeping the top N words. The value `hProd` is the *Hadamard product*, which is nothing more than multiplying matrices element-wise, rather than the usual matrix multiplication of row vector x column vector.

Before writing the output, we convert the matrix back to a Cascading pipe and replace the bookId with the abbreviated book name that we started with. Note that `mapTo` used here is like `map`, but the later keeps all the original input fields and adds the new fields created by the `map` function. In contrast, `mapTo` tosses all the original fields that aren't explicitly passed to the anonymous function. Hence, it's equivalent to the `map(...).project(...)` sequence, but more efficient by eliminating the extra intermediate step.

Finally, before writing the output, we see how joins work, which we use to bring in a table of the book abbreviations and the corresponding full names. We would rather write the full names. Otherwise, this join isn't necessary. 

This mapping is in the file `data/abbrevs-to-names.tsv`. Almost a third of the books are actually aprocryphal and hence aren't in the KJV. We'll effectively ignore those.

Note the word `Tiny` in the inner join, `joinWithTiny`. The "tiny" pipe is the abbreviations data set on the "right-hand side. Knowing which pipe has the smallest data set is important, because Cascading can use a optimization known as a *map-side join*. In short, if the small data set can fit in memory, that data can be cached in the underlying Hadoop "map" process' memory and the join can be performed as the larger pipe of data streams through. For more details on this optimization, see [this description for Hive's version of map-side joins](https://cwiki.apache.org/confluence/display/Hive/LanguageManual+JoinOptimization#LanguageManualJoinOptimization-PriorSupportforMAPJOIN).

The pair tuple passed to `joinWithTiny` lists one or more fields from the left-hand table and a corresponding number of fields from the right-hand table to join on. Here, we just join on a single field from each pipe. If it were two, we would pass an argument like `(('l1, l2') -> ('r1, 'r2))`. Note the nested tuples within the outer pair tuple.

After joining, we do a final projection and then write the output as before.

## Next Steps

There are additional capabilities built into this Activator template that you can access from a command line using the `activator` command or [sbt](http://www.scala-sbt.org/), the standard Scala build tool. Let's explore those features.

## Running Locally with the Command Line

In a command window, run `activator` (or `sbt`). At the prompt, invoke the `test` and `run` tasks, which are the same tasks we ran in Activator, to ensure they complete successfully. 

All four examples take command-line arguments to customize their behavior. The *NGrams* example is particular interesting to play with, where you search for different phrases in the Bible or some other text file of interest to you. 

At the `activator` prompt, type `scalding`. You'll see the following:

```
> scalding
[error] Please specify one of the following commands (example arguments shown):
[error]   scalding FilterUniqueCountLimit --input data/kjvdat.txt --output output/kjv
[error]   scalding NGrams --count 20 --ngrams "I love % %" --input data/kjvdat.txt --output output/kjv-ngrams.txt
[error]   scalding TfIdf --n 100 --input data/kjvdat.txt --output output/kjv-tfidf.txt
[error]   scalding WordCount --input data/kjvdat.txt --output output/kjv-wc.txt
[error] scalding requires arguments.
```

Hence, without providing any arguments, the `scalding` command tells you which scripts are available and the arguments they support with examples that will run with supplied data in the `data` directory. Note that some of the options shown are optional (but which ones isn't indicated; see the comments in the script files). The scripts are listed alphabetically, not in the order we discussed them previously.

Each command should run without error and the output will be written to the file indicated by the `--output` option. You can change the output location to be anything you want.

Let's look at each example. Recall that all the scripts are in `src/main/scala/scalding`. You should look at the files for detailed comments on how they are implemented.

## NGrams

You invoke the *NGrams* script inside `activator` like this:

```
scalding NGrams --count 20 --ngrams "I love % %" --input data/kjvdat.txt --output output/kjv-ngrams.txt
```

The `--ngrams` phrase allows optional "context" words, like the "I love" prefix shown here, followed by two words, indicated by the two "%". Hence, you specify the desired `N` implicitly through the number of "%" placeholders and hard-coded words (4-grams, in this example). 

For example, the phrase "% love %" will find all 3-grams with the word "love" in the middle, and so forth, while the phrase "% % %" will find all 3-grams, period (i.e., without any "context").

The NGram phrase is translated to a regular expression that also replaces the whitespace with a regular expression for arbitrary whitespace.

**NOTE:** In fact, additional regular expression constructs can be used in this string, e.g., `loves?` will match `love` and `loves`. This can be useful or confusing...

The `--count n` flag means "show the top n most frequent matching NGrams". If not specified, it defaults to 20.

Try different NGram phrases and values of count. Try different data sources.

This example also uses the `debug` pipe to dump output to the console. In this case, you'll see the same output that gets written to the output file, which is the list of the NGrams and their frequencies, sorted by frequency descending.

## WordCount

You invoke the script inside `activator` like this:

```
scalding WordCount --input data/kjvdat.txt --output output/kjv-wc.txt
```

Recall that each line of the input actually has the "schema":

```
Abbreviated name of the book of the Bible (e.g., Gen) | chapter | verse | text
```

For example,

```
Gen|1|1| In the beginning God created the heaven and the earth.~
```

We just treat the whole line as text. A nice exercise is to *project* out just the `text` field. See the other scripts for examples of how to do this.

The `--output` argument specifies where the results are written. You just see a few log messages written to the `activator` console. You can use any path you want for this output.

## FilterUniqueCountLimit

You invoke the script inside `activator` like this:

```
scalding FilterUniqueCountLimit --input data/kjvdat.txt --output output/kjv
```

In this case, the `--output` is actually used as a prefix for the four output files discussed previously.

## TfIDf
 
You invoke the script inside `activator` like this:

```
scalding TfIdf --n 100 --input data/kjvdat.txt --output output/kjv-tfidf.txt
````

The `--n` argument is optional; it defaults to 100. It specifies how many words to keep for each document. 


## Running on Hadoop

After testing your scripts, you can run them on a Hadoop cluster. You'll first need to build an all-inclusive jar file that contains all the dependencies, including the Scala standard library, that aren't already on the cluster.

The `activator assembly` command first runs an `update` task, if missing dependencies need to be downloaded. Then the task builds the all-inclusive jar file, which is written to `target/scala-2.10/activator-scalding-X.Y.Z.jar`, where `X.Y.Z` will be the current version number for this project.

One the jar is built and assuming you have the `hadoop` command installed on your system (or the server to which you copy the jar file...), the following command syntax will run one of the scripts

```
hadoop jar target/scala-2.10/activator-scalding-X.Y.Z.jar SCRIPT_NAME \ 
  [--hdfs | --local ] [--host JOBTRACKER_HOST] \ 
  --input INPUT_PATH --output OUTPUT_PATH \ 
  [other-args] 
```

Here is an example for `NGrams`, using HDFS, not the local file system, and assuming the JobTracker host is determined from the local configuration files, so we don't have to specify it:

```
hadoop jar target/scala-2.10/activator-scalding-X.Y.Z.jar NGrams \ 
  --hdfs  --input /data/docs --output output/wordcount \ 
  --count 100 --ngrams "% loves? %"
```

Note that when using HDFS, Hadoop treats all paths as *directories*. So, all the files in an `--input` directory will be read. In `--local` mode, the paths are interpreted as *files*.

An alternative to running the `hadoop` command directly is to use the `scald.rb` script that comes with Scalding distributions. See the [Scalding](https://github.com/twitter/scalding) website for more information.

## Going Forward from Here

This template is not a complete Scalding tutorial. To learn more, see the following:

* The Scalding [Wiki](https://github.com/twitter/scalding/wiki). 
* The Scalding [tutorial](https://github.com/twitter/scalding/tree/develop/tutorial) distributed with the [Scalding](https://github.com/twitter/scalding) distribution. 
* Dean Wampler's [Scalding Workshop](https://github.com/deanwampler/scalding-workshop), from which some of this material was adapted.
* See [Typesafe](http://typesafe.com) for more information about our products and services. 
* See [Typesafe Activator](http://typesafe.com/activator) to find other Activator templates.

