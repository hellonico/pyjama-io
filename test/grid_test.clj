(ns grid-test
  (:require [clojure.java.io :as io]
            [clojure.pprint]
            [clojure.test :refer :all]
            [pyjama.io.core :as pyo]
            [pyjama.io.export]
            [pyjama.io.parallelmap]
            [pyjama.io.print]
            [pyjama.io.readers]
            [pyjama.parallel]))

(deftest preload-data-test
  (let [file "test/parallels/sample01.csv"
        excel-data (pyjama.io.parallelmap/load-data (io/file file))]
    (clojure.pprint/pprint (pyjama.io.parallelmap/construct-map excel-data))))

(deftest indiana-jones-test
  (let [app-state (atom {:url "http://localhost:11434" :tasks {} :processing true})]
    (pyjama.parallel/parallel-generate
      app-state
      {:models  ["llama3.2"]
       :pre     "Answer in three points the following sentence:\n %s"
       :format  {:type     "array"
                 :minItems 3
                 :maxItems 3
                 :items
                 {:type "string"}}
       ;:template "Translate to Japanese the following sentence: %s"
       ;:prompts  ["Why is the sky blue" "Why are the smurfs blue"]
       :prompts ["Why is the sky blue" "who is indiana jones" "who are the ninja turtles"]}
      identity
      (fn [data]
        (swap! app-state assoc :processing false)
        (pyjama.io.export/export-tasks-results @app-state "pruns.csv")
        ;(pyjama.io.export/export-tasks-results @app-state "pruns.xlsx")))
        ))
    (while (:processing @app-state)
      (Thread/sleep 500))))


(deftest blue-sky-test
  (->>
    {:url     "http://localhost:11432,http://localhost:11434"
     :models  ["llama3.1"]
     :format  {:type "integer"}
     :pre     "This is a potential answer %s02 for this question: %s01.
     Give a score to the answer on a scale 1 to 100: based on how accurate it.
       - Do not give an answer yourself.
       - No comment.
       - No explanation.
       - No extra text. "
     :prompts [["Why is the sky blue"
                "The sky appears blue because of a process called Rayleigh scattering."]
               ["Why is the sky blue"
                "Blue is scattered more than other colors because it travels as shorter, smaller waves."]
               ["Why is the sky blue"
                "During the day the sky looks blue because it's the blue light that gets scattered the most. "]
               ["Why is the sky blue"
                "Because it is Christmas. "]
               ]}
    (pyjama.parallel/generate)
    (sort-by :model)                                        ; nice sorting
    (pyjama.io.print/print-table [:prompt :result :duration-ms])))

(deftest resume-test
  (let [pdf (pyo/download-file "https://msnlabs.com/img/resume-sample.pdf")
        resume (pyjama.io.readers/extract-text pdf)
        job-position "financial IT engineer in Japan"]
    (->>
      {:url     "http://localhost:11432"
       :models  ["llama3.1"]
       :pre     ["This is a potential resume %s01 for this job position: %s02.
       With this knowledge, answer as shortly as possible the question: %s03"
                 resume
                 job-position]
       :system  "always answer in a very short less than 50 characters sentence"
       :options {:num_context 15000}
       :prompts ["is the resume relevant"
                 "whats the average previous job length"
                 "what is the most obvious flaw"
                 "what are the 3 worst points of the resume"]}
      (pyjama.parallel/generate)
      (sort-by :model)
      (pyjama.io.print/print-table [:prompt :result]))))