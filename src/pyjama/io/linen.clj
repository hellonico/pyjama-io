(ns pyjama.io.linen
  "Legacy CSV/Excel readers used by the 'linen' workflow.

  - read-excel: returns {:headers [...], :rows [{...} ...]} from the first sheet.
  - read-csv: returns {:headers [...], :rows [{...} ...]}.

  Note: slated for migration; prefer shared, centralized IO utilities where possible."
  (:require
    [clojure.data.csv :as csv]
    [clojure.java.io :as io]
    [dk.ative.docjure.spreadsheet :as ss])
  (:import (org.apache.poi.ss.usermodel Cell)))

; TODO where is this called from
(defn read-excel [file-path]
  (let [workbook (ss/load-workbook file-path)
        ; TODO: support for other sheets than the first one
        ;sheet (ss/select-sheet "Sheet1" workbook)
        sheet (first (ss/sheet-seq workbook))
        rows (ss/row-seq sheet)
        headers (map #(-> % .getStringCellValue keyword)
                     (filter #(instance? Cell %)
                             (ss/cell-seq (first rows))))
        data (map (fn [row]
                    (zipmap headers (map #(when (instance? Cell %)
                                            (try
                                              (.toString %)
                                              (catch Exception _ nil)))
                                         (ss/cell-seq row))))
                  (rest rows))]
    {:headers headers :rows data}))

; TODO where is this called from
(defn read-csv [file-path]
  (with-open [reader (io/reader file-path)]
    (let [lines (doall (csv/read-csv reader))
          headers (map keyword (first lines))
          rows (map #(zipmap headers %) (rest lines))]
      {:headers headers :rows rows})))