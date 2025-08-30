(ns pyjama.io.export
  "Export utilities for writing task results to CSV or Excel.

  - export-tasks-results pivots data into a table of prompts × models and writes:
    - .csv via clojure.data.csv
    - .xlsx via Docjure (sets column widths and text wrapping)

  Also pretty-prints the input data for debugging."
  (:require
    [clojure.data.csv :as csv]
    [clojure.pprint]
    [clojure.java.io :as io]
    [dk.ative.docjure.spreadsheet :as xls])
  (:import (org.apache.poi.ss.usermodel Row)))

(defn export-tasks-results [data output-file]
  (let [
        _ (clojure.pprint/pprint data)
        tasks (vals (:tasks data))

        prompts (distinct (map #(get-in % [:params :prompt]) tasks))
        models (distinct (map #(get-in % [:params :model]) tasks))
        result-map (reduce (fn [acc {:keys [params result]}]
                             (let [{:keys [prompt model]} params]
                               (update-in acc [prompt model] (constantly result))))
                           {} tasks)
        header (into ["prompt"] models)
        rows (map (fn [prompt]
                    (into [prompt] (map #(get-in result-map [prompt %] "") models)))
                  prompts)]
    (cond

      (.endsWith output-file ".csv")
      (with-open [writer (io/writer output-file)]
        (csv/write-csv writer (cons header rows)))

      (.endsWith output-file ".xlsx")
      (let [workbook (xls/create-workbook "Sheet1" (cons header rows))]
        (let [sheet (.getSheet workbook "Sheet1")
              style (xls/create-cell-style! workbook {:border-bottom :thin :wrap          true})]
          (doseq [row (range (count rows))]
            (let [row_ ^Row (.getRow sheet (inc row))]
              (xls/set-row-style! row_ style)
              (doseq [col (range (count header))]
                (let [cell (.getCell row_ col)]
                  (.setColumnWidth sheet col (* 50 300))
                  (xls/set-cell-style! cell style)))))
          (xls/save-workbook! output-file workbook)))

      :else
      (throw (Exception. (str "Unsupported file extension: " output-file))))))