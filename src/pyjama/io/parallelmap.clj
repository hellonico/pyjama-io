(ns pyjama.io.parallelmap
  (:require
    [clojure.data.csv :as csv]
    [clojure.java.io :as io]
    [dk.ative.docjure.spreadsheet :as ss]))

;; Function to read CSV data
(defn read-csv [file]
  (with-open [reader (io/reader file)]
    (doall (csv/read-csv reader))))

;; Function to read Excel data using docjure
(defn read-excel [file]
  (let [workbook (ss/load-workbook file)
        sheet (ss/select-sheet workbook 0)]                 ;; Get the first sheet
    (map (fn [row] (mapv #(ss/read-cell-value %) row)) (ss/row-seq sheet))))

;; Common function to load data based on file type
(defn load-data [file]
  (let [extension (-> file .getName (clojure.string/split #"\.") last)]
    (cond
      (= extension "csv") (read-csv file)
      (or (= extension "xls") (= extension "xlsx")) (read-excel file)
      :else (throw (ex-info "Unsupported file format" {:file file})))))

;; Function to construct the map from the data
(defn construct-map [excel-data]
  (let [headers (first excel-data)
        data (rest excel-data)
        headers-index (zipmap headers (range))  ;; Create a map of header to index
        models (->> (map #(get % (headers-index "models")) data) ;; Accessing models column
                    (filter #(not (empty? %)))
                    (into [])
                    ) ;; Remove empty values
        prompts (->> (map #(get % (headers-index "prompts")) data) ;; Accessing prompts column
                     (filter #(not (empty? %)))) ;; Remove empty values
        pre (some->> (headers-index "pre") (get data) first)
        system (some->> (headers-index "system") (get data) first)]
    (cond-> {:models models :prompts prompts}
            pre (assoc :pre pre)
            system (assoc :system system))))

(defn -main
  "example usage"
  [& args]
  (let [file "sample.csv" ;; Change to the path of your file
        excel-data (load-data (io/file file))]
    (println (construct-map excel-data)))

  )