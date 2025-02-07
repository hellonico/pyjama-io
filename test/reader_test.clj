(ns reader_test
      (:require [pyjama.io.readers :refer [extract-text]]
                [pyjama.io.core :as pyo]
                [clojure.test :refer :all]))

(deftest test-pdf-with-tables
  (println
    (->
      "https://www.w3.org/WAI/WCAG20/Techniques/working-examples/PDF20/table.pdf"
      (pyo/download-file)
      (extract-text))))